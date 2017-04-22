/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package ui;

import core.SimClock;


/**
 * Simple text-based user interface.
 */
public class DTNSimTextUI extends DTNSimUI {
	private long lastUpdateRt;	// real time of last ui update
	private long startTime; // simulation start time
	/** How often the UI view is updated (milliseconds) */
	public static final long UI_UP_INTERVAL = 60000;

	protected void runSim() {
		double simTime = SimClock.getTime();
		double endTime = scen.getEndTime();
	
		print("Running simulation '" + scen.getName()+"'");

		startTime = System.currentTimeMillis();
		lastUpdateRt = startTime;
		
		while (simTime < endTime && !simCancelled){
			try {
				world.update();
			} catch (AssertionError e) {
				e.printStackTrace();
				done();
				return;
			}
			simTime = SimClock.getTime();
			this.update(false);
		}
		/*
		add by NY
		export the router
		 */
		//List hostsList = world.getHosts();
		//exportMap(hostsList);
		double duration = (System.currentTimeMillis() - startTime)/1000.0;
		
		simDone = true;
		done();
		this.update(true); // force final UI update
		
		print("Simulation done in " + String.format("%.2f", duration) + "s");
	
	}
	/*
	add by NY
	export the router data into local


	void exportMap(List hostsList){
		FileOutputStream outStream = null;
		ObjectOutputStream objectOutputStream = null;
		DTNHost host;
		MessageRouter mRouter;
		TrainBayesianRouter bRouter;
		for (int i=0,n = hostsList.size(); i<n; i++) {
			host = (DTNHost) hostsList.get(i);
			mRouter = host.getRouter();
			bRouter = (TrainBayesianRouter) mRouter;
			HashMap routerMap = (HashMap) bRouter.getRouterMap();
			try {
				File file = new File("C:/TheOne/Bayesian/testmapRouter.HashMap");
				outStream = new FileOutputStream(file,true);
				if(file.length()<1){
					objectOutputStream = new ObjectOutputStream(outStream);
				}else{
					objectOutputStream = new MyObjectOutputStream(outStream);
				}
				objectOutputStream.writeObject(routerMap);
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
		try {
			objectOutputStream.writeObject(null);
			outStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	class MyObjectOutputStream extends ObjectOutputStream {
		public MyObjectOutputStream() throws IOException {
			super();
		}
		public MyObjectOutputStream(OutputStream out) throws IOException {
			super(out);
		}
		@Override

		protected void writeStreamHeader() throws IOException {
			return;
		}
	}*/
	
	/**
	 * Updates user interface if the long enough (real)time (update interval)
	 * has passed from the previous update.
	 * @param forced If true, the update is done even if the next update
	 * interval hasn't been reached.
	 */
	private void update(boolean forced) {
		long now = System.currentTimeMillis();
		long diff = now - this.lastUpdateRt;
		double dur = (now - startTime)/1000.0;
		if (forced || (diff > UI_UP_INTERVAL)) {
			// simulated seconds/second calc
			double ssps = ((SimClock.getTime() - lastUpdate)*1000) / diff;
			print(String.format("%.1f %d: %.2f 1/s", dur, 
					SimClock.getIntTime(),ssps));
			
			this.lastUpdateRt = System.currentTimeMillis();
			this.lastUpdate = SimClock.getTime();
		}		
	}
	
	private void print(String txt) {
		System.out.println(txt);
	}
	
}
