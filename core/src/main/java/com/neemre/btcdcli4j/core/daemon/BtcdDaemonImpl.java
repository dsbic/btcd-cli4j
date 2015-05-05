package com.neemre.btcdcli4j.core.daemon;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.neemre.btcdcli4j.core.NodeProperties;
import com.neemre.btcdcli4j.core.client.BtcdClient;
import com.neemre.btcdcli4j.core.daemon.event.AlertListener;
import com.neemre.btcdcli4j.core.daemon.event.BlockListener;
import com.neemre.btcdcli4j.core.daemon.event.WalletListener;
import com.neemre.btcdcli4j.core.daemon.notification.NotificationMonitor;

public class BtcdDaemonImpl implements BtcdDaemon {
	
	private static final Logger LOG = LoggerFactory.getLogger(BtcdDaemonImpl.class);
	
	private DaemonConfigurator configurator;
	private Map<Notifications, NotificationMonitor> monitors;
	private Map<Notifications, Future<Void>> futures;
	private ExecutorService monitorPool;

	private BtcdClient client;
	
	
	private BtcdDaemonImpl() {
		LOG.info("** BtcdDaemonImpl(): initiating the 'bitcoind' notification daemon");
		configurator = new DaemonConfigurator();
		monitors = new HashMap<Notifications, NotificationMonitor>();
		futures = new HashMap<Notifications, Future<Void>>();
	}
	
	public BtcdDaemonImpl(BtcdClient btcdProvider) {
		this(btcdProvider, new Properties());
	}
	
	public BtcdDaemonImpl(BtcdClient btcdProvider, Properties nodeConfig) {
		this();
		this.client = configurator.checkBtcdProvider(btcdProvider);
		buildMonitors(configurator.checkNodeConfig(nodeConfig));
		startMonitors();
	}

	public BtcdDaemonImpl(BtcdClient btcdProvider, Integer alertPort, Integer blockPort, 
			Integer walletPort) throws IOException {
		this();
		this.client = configurator.checkBtcdProvider(btcdProvider);
		buildMonitors(configurator.checkNodeConfig(alertPort, blockPort, walletPort));
		startMonitors();
	}

	@Override
	public void addAlertListener(AlertListener listener) {
		monitors.get(Notifications.ALERT).addObserver(listener.getObserver());
	}
	
	@Override
	public int countAlertListeners() {
		return monitors.get(Notifications.ALERT).countObservers();
	}
	
	@Override
	public void removeAlertListener(AlertListener listener) {
		monitors.get(Notifications.ALERT).deleteObserver(listener.getObserver());
	}
	
	@Override
	public void removeAlertListeners() {
		monitors.get(Notifications.ALERT).deleteObservers();
	}

	@Override
	public void addBlockListener(BlockListener listener) {
		monitors.get(Notifications.BLOCK).addObserver(listener.getObserver());
	}
	
	@Override
	public int countBlockListeners() {
		return monitors.get(Notifications.BLOCK).countObservers();
	}
	
	@Override
	public void removeBlockListener(BlockListener listener) {
		monitors.get(Notifications.BLOCK).deleteObserver(listener.getObserver());
	}
	
	@Override
	public void removeBlockListeners() {
		monitors.get(Notifications.BLOCK).deleteObservers();
	}
	
	@Override
	public void addWalletListener(WalletListener listener) {
		monitors.get(Notifications.WALLET).addObserver(listener.getObserver());
	}

	@Override
	public int countWalletListeners() {
		return monitors.get(Notifications.WALLET).countObservers();
	}
	
	@Override
	public void removeWalletListener(WalletListener listener) {
		monitors.get(Notifications.WALLET).deleteObserver(listener.getObserver());
	}
	
	@Override
	public void removeWalletListeners() {
		monitors.get(Notifications.WALLET).deleteObservers();
	}

	@Override
	public boolean isMonitoring(Notifications notificationType) {
		Future<?> monitorHandle = futures.get(notificationType);
		if (monitorHandle != null) {
			return !monitorHandle.isDone();
		} else {
			return false;
		}
	}

	@Override
	public boolean isMonitoringAny() {
		for (Notifications notificationType : Notifications.values()) {
			Future<?> monitorHandle = futures.get(notificationType);
			if ((monitorHandle != null) && (!monitorHandle.isDone())) {
				return true;
			} 
		}
		return false;
	}

	@Override
	public boolean isMonitoringAll() {
		for (Notifications notificationType : Notifications.values()) {
			Future<?> monitorHandle = futures.get(notificationType);
			if ((monitorHandle == null) || (monitorHandle.isDone())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void shutdown() {
		monitorPool.shutdownNow();
	}
	
	private void buildMonitors(Properties nodeConfig) {
		int alertPort = Integer.parseInt(nodeConfig.getProperty(NodeProperties.ALERT_PORT.getKey()));
		monitors.put(Notifications.ALERT, new NotificationMonitor(Notifications.ALERT, alertPort, 
				null));
		int blockPort = Integer.parseInt(nodeConfig.getProperty(NodeProperties.BLOCK_PORT.getKey()));
		monitors.put(Notifications.BLOCK, new NotificationMonitor(Notifications.BLOCK, blockPort, 
				client));
		int walletPort = Integer.parseInt(nodeConfig.getProperty(NodeProperties.WALLET_PORT.getKey()));
		monitors.put(Notifications.WALLET, new NotificationMonitor(Notifications.WALLET, walletPort, 
				client));
		monitorPool = Executors.newFixedThreadPool(monitors.size());
	}

	private void startMonitors() {
		for(Notifications notificationType : monitors.keySet()) {
			NotificationMonitor monitor = monitors.get(notificationType);
			futures.put(notificationType, monitorPool.submit(monitor, (Void)null));
		}
	}
}