package com.example.aplicacionbul;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.aplicacionbul.event.UiToastEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.Collections;
import java.util.Set;

public class DeviceListActivity extends Activity {

    public static final  String EXTRA_DEVICE_ADDRESS = "device_address";
    private BluetoothAdapter mBtAdapter;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBtAdapter==null){
            EventBus.getDefault().post(new UiToastEvent(getString(R.string.text_bluetooth_adapter_error), true, true));
                    finish();
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.activity_device_list);
        //cancelar el layout de dispostivos
        setResult(Activity.RESULT_CANCELED);
        //creamos el adaptador
        ArrayAdapter<String> pairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.item_device_name);
        // Vinculamos nuestro adaptador a la lista de items
        ListView pairedListView = findViewById(R.id.paired_devices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);
        //siguiendo la documentacion de BT
        Set<BluetoothDevice> pairedDevices = Collections.emptySet();
        try {
            //Obtenermos la lista de dispositivos BT YA sincronizados
            pairedDevices = mBtAdapter.getBondedDevices(); // ALMACENAMOS EN LA LSTA VACIA
        }catch (Exception e){
            EventBus.getDefault().post(new UiToastEvent(getString(R.string.text_bluetooth_adaptor_error), true, true));
            finish();
        }
        if (pairedDevices.size() > 0) {
            //Barremos la lista de dispositivos sincronizados
            for (BluetoothDevice device : pairedDevices) {
                // jalamos el nombre del dispositivo y su MAC
                pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            pairedDevicesArrayAdapter.add(getString(R.string.text_no_paired_devices));
        }
    }

    // Respondemos al evento click de cada item de la lista
    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {

        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            //cancelamos la busqueda de dispositivos
            mBtAdapter.cancelDiscovery();

            String info = ((TextView) v).getText().toString();
            //recuperamos la MAC
            if(info.length() > 16){
                String address = info.substring(info.length() - 17);
                Intent intent = new Intent();
                intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

                setResult(Activity.RESULT_OK, intent);
                finish();
            }

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBtAdapter != null) {
            //DEA DE BUSCAR DISPOSITIVOS
            mBtAdapter.cancelDiscovery();
        }
    }
}