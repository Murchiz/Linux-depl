package com.linuxdeployer;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.linuxdeployer.deploy.LinuxDeployer;
import com.linuxdeployer.net.NetworkUtils;
import com.linuxdeployer.terminal.TerminalView;

public class MainActivity extends Activity {

    private LinuxDeployer deployer;
    private TerminalView terminalView;
    private TextView tvIp;
    private EditText etInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        deployer = new LinuxDeployer(this);
        tvIp = (TextView) findViewById(R.id.tv_ip);
        terminalView = (TerminalView) findViewById(R.id.terminal_view);
        etInput = (EditText) findViewById(R.id.et_input);

        tvIp.setText("LAN IP: " + NetworkUtils.getLocalIPv4Address());

        deployer.mount();
        deployer.startSshd();

        terminalView.startShell(deployer.getChrootCommand());

        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String cmd = etInput.getText().toString();
                    terminalView.writeCommand(cmd);
                    etInput.setText("");
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deployer != null) {
            deployer.unmount();
        }
    }
}
