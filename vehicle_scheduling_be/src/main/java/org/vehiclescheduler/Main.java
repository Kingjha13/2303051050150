package org.vehiclescheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.evallogger.AuthTokenManager;
import org.evallogger.EvalConfig;
import org.evallogger.LogClient;
import org.vehiclescheduler.model.Depot;
import org.vehiclescheduler.model.DepotsResponse;
import org.vehiclescheduler.model.VehicleTask;
import org.vehiclescheduler.model.VehiclesResponse;

import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        EvalConfig config = new EvalConfig();
        AuthTokenManager tokenManager = AuthTokenManager.fromConfig(config);
        LogClient log = new LogClient(tokenManager);
        EvalApiClient api = new EvalApiClient(tokenManager, log);
        ObjectMapper mapper = new ObjectMapper();

        log.Log("backend", "info", "service", "Vehicle Maintenance Scheduler starting run");

        DepotsResponse depotsResp = api.fetchDepots();
        VehiclesResponse vehiclesResp = api.fetchVehicles();
        List<Depot> depots = depotsResp.depots;
        List<VehicleTask> vehicles = vehiclesResp.vehicles;

        ArrayNode report = mapper.createArrayNode();

        for (Depot depot : depots) {
            log.Log("backend", "info", "service",
                    "Solving maintenance schedule for depot " + depot.getId()
                            + " (budget=" + depot.getMechanicHours() + "h, candidateTasks=" + vehicles.size() + ")");

            KnapsackSolver.Result result = KnapsackSolver.solve(vehicles, depot.getMechanicHours());

            ObjectNode depotNode = mapper.createObjectNode();
            depotNode.put("depotId", depot.getId());
            depotNode.put("mechanicHoursBudget", depot.getMechanicHours());
            depotNode.put("mechanicHoursUsed", result.totalDuration);
            depotNode.put("totalImpactScore", result.totalImpact);

            ArrayNode selectedNode = mapper.createArrayNode();
            for (VehicleTask t : result.selected) {
                ObjectNode taskNode = mapper.createObjectNode();
                taskNode.put("taskId", t.getTaskId());
                taskNode.put("duration", t.getDuration());
                taskNode.put("impact", t.getImpact());
                selectedNode.add(taskNode);
            }
            depotNode.set("selectedTasks", selectedNode);
            report.add(depotNode);

            log.Log("backend", "info", "service",
                    "Depot " + depot.getId() + " schedule complete: "
                            + result.selected.size() + " tasks selected, "
                            + result.totalDuration + "/" + depot.getMechanicHours() + "h used, "
                            + "impact=" + result.totalImpact);
        }

        log.Log("backend", "info", "service", "Vehicle Maintenance Scheduler run complete");

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
