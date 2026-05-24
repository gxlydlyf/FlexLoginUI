package com.gxlydlyf.flexloginui;

public class LangUtil extends ConfigAbstract {
    public LangUtil(String externalConfigPath, String internalResourcePath) {
        super(externalConfigPath, internalResourcePath);
    }

    @Override
    protected int getLatestVersion() {
        return 1;
    }

    @Override
    protected void registerMigrations() {

    }
}
