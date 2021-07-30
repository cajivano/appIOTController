package com.example.aplicacionbul;

import android.app.Activity;
import android.app.NotificationChannel;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.aplicacionbul.event.UiToastEvent;
import com.example.aplicacionbul.helper.EnhancedSharedPreferences;
import com.example.aplicacionbul.helper.NotificationHelper;
import com.example.aplicacionbul.service.MyBluetoothSerialService;
import com.example.aplicacionbul.util.Config;
import com.example.aplicacionbul.util.Constants;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class RobotActivity extends AppCompatActivity {

    private RobotActivity.MyServiceMessageHandler myServiceMessageHandler;
    protected MyBluetoothSerialService myBluetoothSerialService = null;
    private BluetoothAdapter bluetoothAdapter = null;
    //bandera de servicio
    private boolean mBoundService = false;
    private String mConnectedDeviceName = null;
    private EnhancedSharedPreferences sharedPref;

    private ImageView imgUp, imgDown, imgLight, imgLeft, imgRight, imgStop, imgConnect, imgSlow, imgFast, imgSound;

    final static String UP="W";
    final static String DOWN="S";
    final static String LEFT="A";
    final static String RIGHT="D";
    final static String LIGHT="O"; // SE USO PARA MOSTRAR LA MAC DEL DISPOSITIVO CONECTADO
    final static String STOP="S";
    final static String SLOW="L";
    final static String FAST="R";
    final static String SOUND= "M";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_modelopanel);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //crear notificaciones
        NotificationHelper notificationHelper = new NotificationHelper(this);
        notificationHelper.createChannels();

        //check support
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter==null){
            Config.Mensaje(this, getString(R.string.text_no_bluetooth_adapter), false, false);
        } else {
            Intent intent = new Intent(getApplicationContext(), MyBluetoothSerialService.class);
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        //hilo para responder estados

        myServiceMessageHandler = new RobotActivity.MyServiceMessageHandler(this, this);

        inicializarControles();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        sharedPref = EnhancedSharedPreferences.getInstance(getApplicationContext(),getString(R.string.shared_preference_key));
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyBluetoothSerialService.MySerialServiceBinder binder = (MyBluetoothSerialService.MySerialServiceBinder) service;
            myBluetoothSerialService = binder.getService();
            mBoundService = true;
            myBluetoothSerialService.setMessageHandler(myServiceMessageHandler);
            myBluetoothSerialService.setStatusUpdatePoolInterval(
                    Long.parseLong(sharedPref.getString(getString(
                            R.string.preference_update_pool_interval
                    ), String.valueOf(Constants.STATUS_UPDATE_INTERVAL)))
            );
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBoundService = false;
        }
    };
    @Override
    protected void onResume() {
        super.onResume();

        //forzamos el BT
        if (!bluetoothAdapter.isEnabled()){
            Thread thread = new Thread(){
                @Override
                public void run() {
                    try{
                        bluetoothAdapter.enable(); //activar
                    }catch (RuntimeException e){
                        EventBus.getDefault().post(new UiToastEvent(getString(R.string.text_no_bluetooth_permission),true,true));
                    }
                }
            };
            thread.start();
        }
        if(myBluetoothSerialService != null) onBluetoothStateChange(myBluetoothSerialService.getState());
    }
private static  class MyServiceMessageHandler extends Handler {
        private final WeakReference<RobotActivity> mActivity;
        private final Context mContext;

        MyServiceMessageHandler(Context context, RobotActivity activity){
            mContext = context;
            mActivity = new WeakReference<>(activity);
        }
        @Override
    public void handleMessage(Message msg){
            switch (msg.what){
                case Constants.MESSAGE_STATE_CHANGE:
                    mActivity.get().onBluetoothStateChange(msg.arg1);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    mActivity.get().mConnectedDeviceName= msg.getData().getString(Constants.DEVICE_NAME);
                    Config.Mensaje(mContext, mActivity.get().getString(R.string.text_connected_to),true, true);
                    break;
                case Constants.MESSAGE_TOAST:
                   Config.Mensaje(mContext, msg.getData().getString(Constants.TOAST), false, false );
                    break;
            }
        }
}

private void onBluetoothStateChange(int currentState){
        switch (currentState){
            case MyBluetoothSerialService.STATE_CONNECTED:
                break;
            case MyBluetoothSerialService.STATE_CONNECTING:
                break;
            case MyBluetoothSerialService.STATE_LISTEN:
                break;
            case MyBluetoothSerialService.STATE_NONE:
                break;
        }
}



    private void getNotification() {
        //codigo de documentacion para notificaciones
        //crear notificaciones
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), NotificationHelper.CHANNEL_SERVICE_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.text_bluetooth_service))
                .setContentText(getString(R.string.text_bluetooth_service_foreground_message))
                .setColor(getResources().getColor(R.color.colorPrimary))
                .setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(0, builder.build());

    }

    private void inicializarControles() {
        imgUp = findViewById(R.id.img_up);
        imgDown = findViewById(R.id.img_down);
        imgLeft = findViewById(R.id.img_left);
        imgRight = findViewById(R.id.img_right);
        imgLight = findViewById(R.id.img_light);
        imgStop = findViewById(R.id. img_stop);
        imgConnect = findViewById(R.id.img_connect);
        imgSlow = findViewById(R.id.img_slow);
        imgFast = findViewById(R.id.img_fast);
        imgSound = findViewById(R.id.img_sound);
        //para cada uno de los controles
        imgUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton arriba "+ UP, true, false));
            }
        });

        imgDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton abajo "+ DOWN, true, false));
            }
        });

        imgLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton izquierda "+ LEFT, true, false));
            }
        });

        imgRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton Derecha "+ RIGHT, true, false));
            }
        });
        imgSlow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton ir despacio "+ SLOW, true, false));

            }
        });
        imgFast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton ir rápido "+ FAST, true, false));

            }
        });
        imgSound.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton sonido "+ SOUND, true, false));

            }
        });

        imgLight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String macAddress = sharedPref.getString(getString(R.string.preference_last_connected_device),
                        "");
                Toast.makeText(RobotActivity.this, "Mac Address "+ macAddress, Toast.LENGTH_LONG).show();
            }
        });
        imgStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EventBus.getDefault().post(new UiToastEvent("Boton detener "+ STOP, true, false));
            }
        });
        imgConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(RobotActivity.this, DeviceListActivity.class);
                startActivityForResult(intent, Constants.CONNECT_DEVICE_SECURE);
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case Constants.CONNECT_DEVICE_INSECURE:
            case Constants.CONNECT_DEVICE_SECURE:
            if(resultCode== Activity.RESULT_OK){
                mConnectedDeviceName = Objects.requireNonNull(data.getExtras()).getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                sharedPref.edit().putString(getString(R.string.preference_last_connected_device),
                        mConnectedDeviceName).apply();
                Log.e("MI_DATO", mConnectedDeviceName);
                connectToDevice(mConnectedDeviceName);
            }
        }
    }
    private void connectToDevice(String macAddress) {
        if (macAddress == null) {
            Intent serverIntent = new Intent(getApplicationContext(), DeviceListActivity.class);
            startActivityForResult(serverIntent, Constants.CONNECT_DEVICE_SECURE);
        } else {
            Intent intent = new Intent(getApplicationContext(), MyBluetoothSerialService.class);
            intent.putExtra(MyBluetoothSerialService.KEY_MAC_ADDRESS, macAddress);
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                getApplicationContext().startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mBoundService){
            myBluetoothSerialService.setMessageHandler(null);
            unbindService(serviceConnection);
            mBoundService = false;
        }
        stopService(new Intent(this, MyBluetoothSerialService.class));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUiToastEvent(UiToastEvent event) {
        Config.Mensaje(RobotActivity.this, event.getMessage(), event.getLongToast(), event.getIsWarning());
    }
}
