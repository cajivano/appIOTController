package com.example.aplicacionbul.service;


import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.aplicacionbul.R;
import com.example.aplicacionbul.event.UiToastEvent;
import com.example.aplicacionbul.helper.NotificationHelper;
import com.example.aplicacionbul.util.Constants;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class MyBluetoothSerialService extends Service {

    private static final String TAG = MyBluetoothSerialService.class.getSimpleName();
    public static final String KEY_MAC_ADDRESS = "KEY_MAC_ADDRESS";

    private static final UUID MY_UUID_SECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //adaptador bluetooth
    BluetoothAdapter mAdapter;
    //inicio hilos
    ///estado de conexion / PARA INDICAR ESTADO DE CONEC
    private Handler mHandlerActivity;
    ///hilo para conectar
    private ConnectThread mConnectThread;
    ///hilo de conexion relaizada / PARA DETECTAR ENTRADAS Y SALIDAS
    private ConnectedThread mConnectedThread;
    //fin hilos

    //codigo documentacion
    private int mState;
    private int mNewState;
    //ESTADOS CONEXION
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    //ENLACE CON NUESTRO SERVICIO
    private final IBinder mBinder = new MySerialServiceBinder();
    // CADA CUANTO TIEMPO SE ACTUALIZA EL SERVICIO
    private long statusUpdatePoolInterval = Constants.STATUS_UPDATE_INTERVAL;

    @Override
    public void onCreate(){
        super.onCreate();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        // por ser primera vez que se crea
        mState = STATE_NONE;
        mNewState = mState;
        //comprobamos si dispositivo soporta bluetooth
        if(mAdapter == null){
            //no
            EventBus.getDefault().post(new UiToastEvent(getString(R.string.text_bluetooth_adapter_error), true, true));
            stopSelf(); //detiene
        } else {
            //si
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1){
                startForeground(Constants.BLUETOOTH_SERVICE_NOTIFICATION_ID, this.getNotification(null));
            }
        }
    }

    private Notification getNotification(String message){
        if(message == null) message = getString(R.string.text_bluetooth_service_foreground_message);
        return new NotificationCompat.Builder(getApplicationContext(), NotificationHelper.CHANNEL_SERVICE_ID)
                .setContentTitle(getString(R.string.text_bluetooth_service))
                .setContentText(message)
                .setSmallIcon(getResources().getColor(R.color.colorPrimary))
                .setAutoCancel(true).build();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.start();

        if (intent != null) {
            String deviceAddress = intent.getStringExtra(KEY_MAC_ADDRESS);
            if (deviceAddress != null) {
                try {
                    BluetoothDevice device = mAdapter.getRemoteDevice(deviceAddress.toUpperCase());
                    //conexion al modulo
                    this.connect(device, false);
                } catch (IllegalArgumentException e) {
                    EventBus.getDefault().post(new UiToastEvent(e.getMessage(), true, true));
                    //desconectar serivico
                    disconnectService();
                    //detener servicio
                    stopSelf();
                }
            }
        } else {
            EventBus.getDefault().post(new UiToastEvent(getString(R.string.text_unknown_error), true, true));
            disconnectService();
            stopSelf();
        }
        //SEGUN documentacion, indica que cuando salimos el servicio no se detendra
        return Service.START_NOT_STICKY;
    }

    synchronized void start() {
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        updateUserInterfaceTitle();
    }
    synchronized void connect(BluetoothDevice device, boolean secure) {
        if(mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        updateUserInterfaceTitle();
    }

    public void disconnectService(){
        this.stoop();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public synchronized int getState() {
        return mState;
    }

    public class MySerialServiceBinder extends Binder {
        public MyBluetoothSerialService getService() {
            return MyBluetoothSerialService.this;
        }
    }

    public void setMessageHandler(Handler myServiceMessageHandler) {
        this.mHandlerActivity = myServiceMessageHandler;
    }



    //creac conexion como cliente
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType: " + mSocketType);
            setName("ConnectThread" + mSocketType);
            //para optimizar conexion
            mAdapter.cancelDiscovery();

            //conexion al socket bluetooth
            try {
                mmSocket.connect();
            } catch (IOException | NullPointerException e) {
                try {
                    mmSocket.close();
                } catch (IOException | NullPointerException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType + "socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }
            synchronized (this) {
                mConnectThread = null;
            }
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "close() of connect " + mSocketType + "Socket failed", e);
            }
        }


        private synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            // creamos el constructor
            mConnectedThread = new ConnectedThread(socket, socketType);
            mConnectedThread.start();

            if (mHandlerActivity != null) {
                Message msg = mHandlerActivity.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.DEVICE_NAME, device.getName());
                msg.setData(bundle);
                mHandlerActivity.sendMessage(msg);
            }
            updateUserInterfaceTitle();

            try {
                wait(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConenectedThread");
        }

        public void write(byte[] buffer) {
            try {
                //emviamos dato al arduino
                mmOutStream.write(buffer);
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "EXCEPTION DURING WRITE", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException | NullPointerException e) {
                Log.e(TAG, "close() og connect socket failed", e);
            }
        }
    }


        synchronized void stoop() {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            mState = STATE_NONE;
            updateUserInterfaceTitle();
        }

        private void connectionFailed() {
            if (mHandlerActivity != null) {
                Message msg = mHandlerActivity.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST, getString(R.string.text_unable_to_connect_to_device));
                msg.setData(bundle);
                mHandlerActivity.sendMessage(msg);
            }
            mState = STATE_NONE;
            updateUserInterfaceTitle();

            this.start();
        }

        private void connectionLost() {
            if (mHandlerActivity != null) {
                Message msg = mHandlerActivity.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST, getString(R.string.text_device_connection_was_lost));
                msg.setData(bundle);
                mHandlerActivity.sendMessage(msg);
            }
            mState = STATE_NONE;
            updateUserInterfaceTitle();
            this.start();

        }

        private void serialWriteBytes(byte[] b) {
            ConnectedThread r;
            synchronized (this) {
                if (mState != STATE_CONNECTED) return;
                r = mConnectedThread;
            }
            r.write(b);
        }

        public void serialWriteString(String s) {
            byte buffer[] = s.getBytes();
            this.serialWriteBytes(buffer);
            Log.d("send_data: ", "caracter enviado " + s);
        }

        public void serialWriteByte(byte b) {
            byte[] c = {b};
            serialWriteBytes(c);
        }

        private synchronized void updateUserInterfaceTitle() {
            mState = getState();
            mNewState = mState;
            if (mHandlerActivity != null) {
                mHandlerActivity.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
            }
        }
        public long getStatusUpdatePoolInterval() { return this.statusUpdatePoolInterval; }

        public void setStatusUpdatePoolInterval (long poolInterval){
        this.statusUpdatePoolInterval = poolInterval;
        }
    }









