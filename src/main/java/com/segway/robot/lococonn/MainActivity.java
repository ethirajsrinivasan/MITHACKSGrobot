package com.segway.robot.lococonn;

import android.app.Activity;

import android.os.Bundle;

import android.util.Log;

import android.widget.Toast;

import com.segway.robot.sdk.base.bind.ServiceBinder;

import com.segway.robot.sdk.baseconnectivity.Message;
import com.segway.robot.sdk.baseconnectivity.MessageConnection;
import com.segway.robot.sdk.baseconnectivity.MessageRouter;
import com.segway.robot.sdk.connectivity.RobotException;
import com.segway.robot.sdk.connectivity.RobotMessageRouter;
import com.segway.robot.sdk.connectivity.StringMessage;

import com.segway.robot.sdk.locomotion.sbv.AngularVelocity;
import com.segway.robot.sdk.locomotion.sbv.Base;
import com.segway.robot.sdk.locomotion.sbv.LinearVelocity;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    private boolean isBind = false;
    private static final String TAG = "RobotActivity";
    private int press = 0;
    private RobotMessageRouter mRobotMessageRouter = null;
    private MessageConnection mMessageConnection = null;
    Base mBase;
    private static final int RUN_TIME = 2000;
    private Timer mTimer;


    private MessageRouter.MessageConnectionListener mMessageConnectionListener = new RobotMessageRouter.MessageConnectionListener() {
        @Override
        public void onConnectionCreated(final MessageConnection connection) {
            Log.d(TAG, "onConnectionCreated: " + connection.getName());
            mMessageConnection = connection;
            try {
                mMessageConnection.setListeners(mConnectionStateListener, mMessageListener);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    };

    private MessageConnection.ConnectionStateListener mConnectionStateListener = new MessageConnection.ConnectionStateListener() {
        @Override
        public void onOpened() {
            Log.d(TAG, "onOpened: ");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "connected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onClosed(String error) {
            Log.e(TAG, "onClosed: " + error);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "disconnected to: " + mMessageConnection.getName(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private MessageConnection.MessageListener mMessageListener = new MessageConnection.MessageListener() {
        @Override
        public void onMessageSentError(Message message, String error) {

        }

        @Override
        public void onMessageSent(Message message) {
            Log.d(TAG, "onBufferMessageSent: id=" + message.getId() + ";timestamp=" + message.getTimestamp());
        }

        @Override
        public void onMessageReceived(final Message message) {
            Log.d(TAG, "onMessageReceived: id=" + message.getId() + ";timestamp=" + message.getTimestamp());
            if (message instanceof StringMessage) {
                //Hard coded robot value here to start the navigation activity
                if (message.equals("Go")) {
                   if(isBind) {
                       // Add code to grab medicine

                       //moveRobot();
                   }

                }

            } else {
                //message received is BufferMessage
                byte[] bytes = (byte[]) message.getContent();
                Log.d(TAG, "onMessageReceived: file name=" + bytes);

            }
        }
    };

    private void moveRobot() {
        new Thread() {
            //@Override
         public void run() {
                float mLinearVelocity = 0.5f;
                if (isBind) {
                    mBase.setLinearVelocity(mLinearVelocity);
                    //mBase.setAngularVelocity(0.2f);
                }
                // set robot base linearVelocity, unit is rad/s, rand is -PI ~ PI.


                // let the robot run for 2 seconds
                try {
                    Thread.sleep(RUN_TIME);
                } catch (InterruptedException e) {
                }

                // stop
                if (isBind) {
                    mBase.setLinearVelocity(0);
                }

         }
      }.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //get RobotMessageRouter
        mBase = Base.getInstance();
        mRobotMessageRouter = RobotMessageRouter.getInstance();
        mRobotMessageRouter.bindService(this, initMessageBinder);
        mBase.bindService(getApplicationContext(), initBinder);

    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            mRobotMessageRouter.unregister();
        } catch (RobotException e) {
            e.printStackTrace();
        }
        mRobotMessageRouter.unbindService();
        mBase.unbindService();
        Log.d(TAG, "onDestroy: ");

    }

    private ServiceBinder.BindStateListener initBinder = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isBind = true;
            try {
                final LinearVelocity lv = mBase.getLinearVelocity();
                Log.d("Linear Velocity ::" , lv.toString());
                moveRobot();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onUnbind(String reason) {
            isBind = false;
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    };

    private ServiceBinder.BindStateListener initMessageBinder = new ServiceBinder.BindStateListener() {
        @Override
        public void onBind() {
            isBind = true;
            try {
                mRobotMessageRouter.register(mMessageConnectionListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onUnbind(String reason) {
            isBind = false;
            if (mTimer != null) {
                mTimer.cancel();
                mTimer = null;
            }
        }
    };


}
