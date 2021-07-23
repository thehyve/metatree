package io.fairspace.saturn.services.maintenance;

import io.fairspace.saturn.services.BaseApp;

import static javax.servlet.http.HttpServletResponse.*;
import static org.eclipse.jetty.http.MimeTypes.Type.APPLICATION_JSON;
import static spark.Spark.get;

public class MaintenanceApp extends BaseApp {
    private final MaintenanceService maintenanceService;

    public MaintenanceApp(String basePath, MaintenanceService maintenanceService) {
        super(basePath);

        this.maintenanceService = maintenanceService;
    }

    @Override
    protected void initApp() {
        get("/reindex", (req, res) -> {
            res.type(APPLICATION_JSON.asString());
            maintenanceService.startRecreateIndexTask();
            res.status(SC_OK);
            return null;
        });
    }
}
