package robot;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Random;

import robocode.AdvancedRobot;
import robocode.BattleEndedEvent;
import robocode.BulletHitBulletEvent;
import robocode.BulletHitEvent;
import robocode.BulletMissedEvent;
import robocode.CustomEvent;
import robocode.DeathEvent;
import robocode.HitByBulletEvent;
import robocode.HitRobotEvent;
import robocode.HitWallEvent;
import robocode.RobocodeFileOutputStream;
import robocode.Robot;
import robocode.RoundEndedEvent;
import robocode.Rules;
import robocode.ScannedRobotEvent;
import robocode.WinEvent;
import robocode.Event;
import robocode.util.Utils;

//import robocode.


public class QBot extends AdvancedRobot {

	
	static States States = new States();
	static Q Q = new Q();
	int reward = 0;
	double epsilon;
	double scantime=0;
	static int run=0;
	
	static int state1=0, state2=0, action1=0;
	static boolean firstrun = true;
	double startTime=0, endTime=0;
	static int roundCount = 0;
	long time[] = new long[5000];
	static int turnsReached[] = new int[5000];
	
	private byte moveDirection = 1;
	
	static int roundCountTotal =0;
	static int winsCount = 0;
	static ArrayList<Integer> winsRate = new ArrayList<Integer>();
	static int totalcountQ3=0;
	public void run() {
	
			if(roundCountTotal >= 50)
			{
				winsRate.add(winsCount*2);
				roundCountTotal = 0;
				winsCount = 0;
			}
			roundCountTotal++;
			
		epsilon = 0.1;
		
	    setAdjustGunForRobotTurn(true);
	    setAdjustRadarForGunTurn(true);
	    turnRadarRightRadians(Double.POSITIVE_INFINITY);
	    
		while (true) {
			doMove();
			learn();
		}
	}

	private void executeAction(int action, double variable) 
	{
		switch(action){
			case 0:	 attackFace();
				break;
			case 1:	 fire(variable);
				break;
			case 2:  avoid();
				break;
			case 3:	runAway();
				break;
		}
		
	}


	public void avoid()
	{
		// switch directions if we've stopped
		if (getVelocity() == 0)
			moveDirection *= -1;

		// circle our enemy
		setTurnRight(States.bearing + 90);
		setAhead(100 * moveDirection);
		
		if (getTime() % 100 == 0) {
			fire(1);
		}
	}
	
	public void attackFace()
	{
		setTurnRight(States.bearing);
		setAhead(100);
		
		if (getTime() % 100 == 0) {
			fire(1);
		}
	
	}
	
	public void runAway()
	{
		setTurnRight(States.bearing - 10);
		setAhead(-100);
		
		if (getTime() % 100 == 0) {
			fire(1);
		}
	}
	
	double normalizeBearing(double angle) {
		while (angle >  180) angle -= 360;
		while (angle < -180) angle += 360;
		return angle;
	}
	
	public void onScannedRobot(ScannedRobotEvent e) {

      double absbearing_rad = (getHeading()+e.getBearing())%(360);
      //this section sets all the information about our target
      double enemyX = getX()+Math.sin(absbearing_rad)*e.getDistance(); //works out the x coordinate of where the target is
      double enemyY = getY()+Math.cos(absbearing_rad)*e.getDistance(); //works out the y coordinate of where the target is
      scantime = getTime();
      
		//Update Variables
		States.update(getEnergy(),
					e.getDistance(),
					getGunHeat(),
					0,
					0);
		
		double firePower = Math.min(400 / e.getDistance(), 3);
	    //  calculate gun turn toward enemy
	    double turn = getHeading() - getGunHeading() + e.getBearing();
	    // normalise the turn to take the shortest path there
	    setTurnGunRight(normalizeBearing(turn));
	    
	    //QLEARNING
	    if(firstrun)
		{
//			take a random action
			Random randomGenerator = new Random();
			action1 = randomGenerator.nextInt(States.numActions - 1);
			executeAction(action1, firePower);
			firstrun = false;
		}else{
			learn();
			
			int tempState = States.getCurrentState();
			if(tempState != state2)
			{
				
				//state2 is the CURRENT State. state1 is the previous
				//Q LEARNING
				state2 = tempState;
				Q.QLearning(state1, action1, state2, reward);
				//store current state as state1
				state1 = state2;
				//reset reward
				reward = 0;
				
				
				//take action
				if(Math.random() > epsilon )
				{
					action1 = Q.actionOfMaxQ(state1);
					executeAction(action1, firePower);
				}else{
				    Random randomGenerator = new Random();
				    action1 = randomGenerator.nextInt(States.numActions - 1);
				    executeAction(action1, firePower);	
				}
			}
			
			if(action1 == 1 && getGunHeat() != 0)
				reward += -3;
		
			executeAction(action1, firePower);
			}
		
		setTurnRadarLeftRadians(getRadarTurnRemainingRadians());
	}


	public void doMove() {

		// always square off against our enemy
		setTurnRight(States.bearing + 90);

		// penalty at changing position every 20 ticks
		if (getTime() % 20 == 0) {
			moveDirection *= -1;
			setAhead(100 * moveDirection);
		}
	}
	
	public void doMoveOpposite() {

		// always run away from enemy
		setTurnRight(States.bearing + 90);
		
		setAhead(-100);
	}
	
	public void getCloser()
	{
		setTurnRight(normalizeBearing(States.bearing + 90 - (10 * moveDirection)));
	}
	
	  public void onWin(WinEvent event)
	  {
		reward += 10;
		learn();
		saveData();
		
		winsCount++;
		time[roundCount++] = getTime();
	  }
	
	  public void onDeath(DeathEvent event)
	  {
		reward += -10;
		learn();
		saveData();
		time[roundCount++] = getTime();
	  }
		
	  public void onBulletHit(BulletHitEvent e)
	  {

	     double change = e.getBullet().getPower() * 3 ;
	     reward = reward + (int)change;;
	     learn();
	  }
	
	  public void onBulletHitBullet(BulletHitBulletEvent e) 
	  {
	    reward += -3;  
	    learn();
	   }
	  
	  public void onHitRobot(HitRobotEvent e)
	  {
	    reward += -3;
	    
	    moveDirection *= -1; 
	    learn();
	  }
	
	  public void onBulletMissed(BulletMissedEvent e)
	  {
	    double change = -e.getBullet().getPower();
	    reward += (int)change;
	    
	    learn();
	  }
	
	  public void onHitByBullet(HitByBulletEvent e)
	  {

	      double power = e.getBullet().getPower();
	      double change = -3 * power;
	     
	       reward += (int)change;
	       
	       learn();

	  }
	  
	  public void onHitWall(HitWallEvent e)
	  {
	       reward += -3;
	       moveDirection *= -1;
	       ahead(100);
	       learn();
	  }
	  
	  
	

	  private void learn()
	  {
			state2 = States.getCurrentState();
			Q.QLearning(state1, action1, state2, reward);
			//store current state as state1
			state1 = state2;
			//reset reward
			reward = 0;
	  }
	  public void loadData()
	  {
		  
	    try
	    {
	      Q.loadData(getDataFile("data.dat"));
	    }
	    catch (Exception e)
	    {
	    	out.println("Exception trying to load: " + e);
	    }
	  }
	
	  public void saveData()
	  {
	    try
	    {
	      Q.saveData(getDataFile("data.dat"));
	    }
	    catch (Exception e)
	    {
	      out.println("Exception trying to write: " + e);
	    }
	  }
	  
	  public void saveDataTurns()
	  {
	    PrintStream w = null;
	    try
	    {
	      w = new PrintStream(new RobocodeFileOutputStream(getDataFile("turns.dat")));
	      for(int i=0; i<roundCount; i++)
	    	  w.println(turnsReached[i]);



	      if (w.checkError())
	        System.out.println("Could not save the data to file!");
	      w.close();
	    }
	    catch (IOException e)
	    {
	      System.out.println("IOException trying to write to file: " + e);
	    }
	    finally
	    {
	      try
	      {
	        if (w != null)
	          w.close();
	      }
	      catch (Exception e)
	      {
	        System.out.println("Exception trying to close witer of movement file: " + e);
	      }
	    }
	  }

	  
	  public void saveWinRate()
	  {
	    PrintStream w = null;
	    try
	    {
	      w = new PrintStream(new RobocodeFileOutputStream(getDataFile("winRate.dat")));
	      for(int i=0; i<winsRate.size(); i++)
	    	  w.println(winsRate.get(i));



	      if (w.checkError())
	        System.out.println("Could not save the data to file!");
	      w.close();
	    }
	    catch (IOException e)
	    {
	      System.out.println("IOException trying to write to file: " + e);
	    }
	    finally
	    {
	      try
	      {
	        if (w != null)
	          w.close();
	      }
	      catch (Exception e)
	      {
	        System.out.println("Exception trying to close witer of movement file: " + e);
	      }
	    }
	  }

	  public void onRoundEnded(RoundEndedEvent e)
              {
		  		//turns - the number of turns that this round reached.
		  		turnsReached[e.getRound()] = e.getTurns();
		  		roundCount = e.getRound();
		  		saveDataTurns();
              }
	  
	  public void onBattleEnded(BattleEndedEvent e) {
	       saveData();
	       saveWinRate();
	       saveDataTurns();
	   }

}												

