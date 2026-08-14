package cn.ae2bc.core.energy;

/** Loader-neutral contract used by energy distributors to discover compatible endpoints. */
public interface EnergyEndpoint {
    boolean isEnergyEndpointAvailable();
    int receiveExternalEnergy(int maxReceive, boolean simulate);
}
