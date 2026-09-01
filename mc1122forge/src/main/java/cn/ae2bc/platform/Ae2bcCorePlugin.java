package cn.ae2bc.platform;

import java.util.Map;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("AE2 BatchCraft AE2 Integration")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("cn.ae2bc.platform")
public final class Ae2bcCorePlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass() { return new String[] { Ae2IntegrationTransformer.class.getName() }; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) { }
    @Override public String getAccessTransformerClass() { return null; }
}
