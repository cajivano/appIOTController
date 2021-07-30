package com.example.aplicacionbul;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.aplicacionbul.util.Constants;

public class HandlerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_handler);
       // setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

    }

    public void iniciarHandler(View view) {
        Handler mHandler = new Handler(){
            @Override
            public void handleMessage(@NonNull Message msg) {
                Bundle datos = msg.getData();
                Toast.makeText(HandlerActivity.this, datos.getString("key_msg"), Toast.LENGTH_LONG)
                        .show();
            }
        };
        Message msg = new Message();
        Bundle datos = new Bundle();
        datos.putString("key_msg", "Iniciando aplicación :)");
        msg.setData(datos);
        mHandler.sendMessage(msg);
        Intent intent = new Intent(HandlerActivity.this, RobotActivity.class);
        startActivity(intent);
    }

}