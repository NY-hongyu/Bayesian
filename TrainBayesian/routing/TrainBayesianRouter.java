/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import core.*;

import java.io.Serializable;
import java.util.*;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class TrainBayesianRouter extends ActiveRouter implements Serializable {

	private Map<Integer, ArrayList> affRouter;

	/** IDs of the messages that are known to have reached the final dst */
	private Set<AckedMessageInfo> ackedMessageInfos;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public TrainBayesianRouter(Settings s) {
		super(s);
		initAffRouter();
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected TrainBayesianRouter(TrainBayesianRouter r) {
		super(r);
		initAffRouter();
		this.ackedMessageInfos = new HashSet<AckedMessageInfo>();
		//TODO: copy epidemic settings here (if any)
	}

	private void initAffRouter(){
		this.affRouter = new HashMap<Integer,ArrayList>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) { // new connection
			if (con.isInitiator(getHost())) {
				/* initiator performs all the actions on behalf of the
				 * other node too (so that the meeting probs are updated
				 * for both before exchanging them) */

				DTNHost otherHost = con.getOtherNode(getHost());
				MessageRouter mRouter = otherHost.getRouter();

				assert mRouter instanceof TrainBayesianRouter : "TrainBayesian only works "+
						" with other routers of same type";
				TrainBayesianRouter otherRouter = (TrainBayesianRouter) mRouter;

				/* exchange ACKed message data */
				//Set<AckedMessageInfo> tempAckedMessageIds = this.ackedMessageInfos;
				//Set<AckedMessageInfo> tempOtherAckedMessageIds = otherRouter.ackedMessageInfos;
				this.ackedMessageInfos.addAll(otherRouter.ackedMessageInfos);
				otherRouter.ackedMessageInfos.addAll(this.ackedMessageInfos);
				//System.out.println(ackedMessageInfos.toString());
				this.setAckedMessages(ackedMessageInfos);
				otherRouter.setAckedMessages(otherRouter.ackedMessageInfos);

//					/* update both meeting probabilities */
//					probs.updateMeetingProbFor(otherHost.getAddress());
//					otherRouter.probs.updateMeetingProbFor(getHost().getAddress());
//
//					/* exchange the transitive probabilities */
//					this.updateTransitiveProbs(otherRouter.allProbs);
//					otherRouter.updateTransitiveProbs(this.allProbs);
//					this.allProbs.put(otherHost.getAddress(),
//							otherRouter.probs.replicate());
//					otherRouter.allProbs.put(getHost().getAddress(),
//							this.probs.replicate());

			}
		}
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

	private void setAckedMessages(Set diffAcked){
		Iterator<AckedMessageInfo> diffInfo = diffAcked.iterator();
		while(diffInfo.hasNext()){
			AckedMessageInfo ackedInfo = diffInfo.next();
			ArrayList affIndexList = affRouter.get(ackedInfo.getDesHostAddress());
			if(affIndexList!=null){
				for(int i = 0;i < affIndexList.size(); i ++){
					AffIndex ai = (AffIndex)affIndexList.get(i);
					if(ai.getMessageID() == ackedInfo.getAckedMessageId()){
						ai.setTransRes(true);
					}
					//System.out.println("ack is setting : "+ai.getTransRes());
				}
			}
		}
	}
	public ArrayList setAffIndexList(Connection con,Message m){
		ArrayList affIndexList = new ArrayList();
		AffIndex ai  = new AffIndex(m.getId(),getLocationID(),getTimeSlot());
		affIndexList.add(ai);
		return affIndexList;
//		affIndexList.add(con.getMessage().getId());
//		affIndexList.add(getHost().getLocationID());
//		affIndexList.add(getHost().getTimeSlot());
//		affIndexList.add(false);
	}

	public Map getRouterMap(){
		return affRouter;
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

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messages =
				new ArrayList<Tuple<Message, Connection>>();

		Collection<Message> msgCollection = getMessageCollection();

		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
		for (Connection con : getConnections()) {
			DTNHost other = con.getOtherNode(getHost());
			TrainBayesianRouter othRouter = (TrainBayesianRouter) other.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue; // skip messages that the other one has
				}
					// the other node has higher probability of delivery
				messages.add(new Tuple<Message, Connection>(m,con));
				if(affRouter.containsKey(m.getTo().getAddress())){
					ArrayList al = affRouter.get(m.getTo().getAddress());
					AffIndex ai = new AffIndex(m.getId(),getLocationID(),getTimeSlot());
					al.add(ai);
				}else {
					affRouter.put(m.getTo().getAddress(),setAffIndexList(con,m));
				}

			}
		}

		if (messages.size() == 0) {
			return null;
		}
		return tryMessagesForConnected(messages);	// try to send messages
	}


	@Override
	public Message messageTransferred(String id, DTNHost from) {
		Message m = super.messageTransferred(id, from);
		/* was this node the final recipient of the message? */
		if (isDeliveredMessage(m)) {
			this.ackedMessageInfos.add(new AckedMessageInfo(id,m.getTo().getAddress()));
		}
		return m;
	}

	/**
	 * Method is called just before a transfer is finalized
	 * at {@link ActiveRouter#update()}. MaxProp makes book keeping of the
	 * delivered messages so their IDs are stored.
	 * @param con The connection whose transfer was finalized
	 */
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		/* was the message delivered to the final recipient? */
		if (m.getTo() == con.getOtherNode(getHost())) {
			this.ackedMessageInfos.add(new AckedMessageInfo(m.getId(),m.getTo().getAddress()));// yes, add to ACKed messages
			this.deleteMessage(m.getId(), false); // delete from buffer
		}
	}



	@Override
	public TrainBayesianRouter replicate() {
		return new TrainBayesianRouter(this);
	}

	public class AckedMessageInfo{
		private String ackedMessageId;
		private int desHostAddress;
		AckedMessageInfo(String ackedMessageId,int desHostAddress){
			this.ackedMessageId = ackedMessageId;
			this.desHostAddress = desHostAddress;
		}
		public String getAckedMessageId(){
			return this.ackedMessageId;
		}
		public int getDesHostAddress(){
			return this.desHostAddress;
		}
		public String toString(){
			return "["+ackedMessageId+"  "+desHostAddress+"]";
		}
	}

}
