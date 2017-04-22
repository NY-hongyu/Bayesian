/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.*;

import java.io.Serializable;
import java.util.*;

/**
 * Implementation of PRoPHET router as described in 
 * <I>Probabilistic routing in intermittently connected networks</I> by
 * Anders Lindgren et al.
 */
public class BayesianRouter extends ActiveRouter implements Serializable {


	private Map<Integer, ArrayList> affRouter;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public BayesianRouter(Settings s) {
		super(s);
		initAffRouter();
	}

	/**
	 * Copyconstructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected BayesianRouter(BayesianRouter r) {
		super(r);
		initAffRouter();
	}

	/*$$$$ add by NY
	 */

	public void mergeAffRouter(HashMap affMap){

		Iterator ite = affMap.entrySet().iterator();
		while (ite.hasNext()){
			Map.Entry entry = (Map.Entry) ite.next();
			int desHostAddress = (int)entry.getKey();
			if(this.affRouter.containsKey(desHostAddress)){
				mergeArrayAffIndex(desHostAddress,(ArrayList)affMap.get(desHostAddress));
			}else {
				this.affRouter.put(desHostAddress,(ArrayList)affMap.get(desHostAddress));
			}
		}
	}


	public void mergeArrayAffIndex(int desHostAddress,ArrayList al){
		ArrayList thisDesList = affRouter.get(desHostAddress);
		thisDesList.addAll(al);
	}

	/**
	 * Initializes predictability hash
	 */
	private void initAffRouter() {
		this.affRouter = new HashMap<Integer, ArrayList>();
	}


	public Map getRouterMap(){
		return affRouter;
	}
	
	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param deshost The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost deshost) {
		double probLoc,probTime,probRes;
		int listsize;
		if (affRouter.containsKey(deshost.getAddress())) {
			ArrayList<AffIndex> al = affRouter.get(deshost.getAddress());
			listsize = al.size();
			ArrayList numList = getNum(al);
			//System.out.println(numList.toString());
			probRes =(double) (int)numList.get(0)/listsize;
			probLoc =(double) (int)numList.get(1)/listsize;
			probTime =(double) (int)numList.get(2)/listsize;
			//if((int)numList.get(1)!=0&&(int)numList.get(2)!=0){
				//System.out.println(numList.toString());
			//}
			if(probRes==0){
				probRes = 0.01;
			}
			if(probLoc==0){
				probLoc = 0.01;
			}
			if(probTime==0){
				probTime = 0.01;
			}
			//System.out.println(Math.log(probLoc*probRes*probTime));
			return Math.log(probLoc*probRes*probTime);
		}
		else {
			return -20;
		}
	}



	private ArrayList getNum(ArrayList desAffList){
		Iterator<AffIndex> it = desAffList.iterator();
		int resNum = 0;
		int locNum = 0;
		int timeNum = 0;
		while (it.hasNext()){
			AffIndex ai = it.next();
			if(ai.getTransRes()){
				resNum++;
				if(ai.isLocEqual(this.getLocationID())){
					locNum++;
					//System.out.println("Get the equal LocId!!!"+ai.toString());
				}
				if(ai.isTimeEqual(this.getTimeSlot())){
					timeNum++;
					//System.out.println("Get the equal TimeId!!!"+ai.toString());
				}
			}
		}
		ArrayList numList = new ArrayList();
		numList.add(resNum);
		numList.add(locNum);
		numList.add(timeNum);
		return numList;
	}

	private String getLocationID(){
		Coord location = getHost().getLocation();
		int lx = (int) location.getX()/1000;
		int ly = (int) location.getY()/1000;
		return "X" + lx +"Y"+ ly;
	}
	private String getTimeSlot(){
		int time = (int)SimClock.getTime();
		int timeSlot = time/600;
		return "" + timeSlot;
	}



	@Override
	public void update() {
		super.update();
		if (!canStartTransfer() ||isTransferring()) {
			return; // nothing to transfer or is currently transferring
		}

		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}

		tryOtherMessages();
	}



	/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * hop counts and their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages =
				new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();
		/* for all connected hosts that are not transferring at the moment,
		 * collect all the messages that could be sent */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			BayesianRouter othRouter = (BayesianRouter)other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
				if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
					// the other node has higher probability of delivery
					messages.add(new Tuple<Message, Connection>(m,con));
				}
			}
		}

		if (messages.size() == 0) {
			return null;
		}
		// sort the message-connection tuples
		TupleComparator t1 = new TupleComparator();
		Collections.sort(messages,t1);
		return tryMessagesForConnected(messages);
	}
	
	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the 
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator
		<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
				Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((BayesianRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((BayesianRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}
	
	@Override
	public MessageRouter replicate() {
		BayesianRouter r = new BayesianRouter(this);
		return r;
	}

}
