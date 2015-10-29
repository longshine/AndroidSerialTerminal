package lx.app.serial;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;

import java.io.File;
import java.io.IOException;

public class DeviceListActivity extends Activity {

    private UsbSerialPortManager mManager;
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        ListView deviceListView = (ListView) findViewById(R.id.device_list_view);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);

        mManager = UsbSerialPortManager.getInstance(this);

        final ArrayAdapter<UsbSerialPort> adapter = new ArrayAdapter<UsbSerialPort>(this,
                android.R.layout.simple_expandable_list_item_2,
                mManager.getPorts()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row;
                if (convertView == null) {
                    LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = convertView;
                }

                UsbSerialPort port = getItem(position);
                UsbSerialDriver driver = port.getDriver();
                UsbDevice device = driver.getDevice();

                String title = UsbSerialPortManager.getName(device);
                if (title == null)
                    title = String.format("Vendor %s Product %s",
                            HexDump.toHexString((short) device.getVendorId()),
                            HexDump.toHexString((short) device.getProductId()));
                String subtitle = driver.getClass().getSimpleName();

                ((TextView) row.findViewById(android.R.id.text1)).setText(title);
                ((TextView) row.findViewById(android.R.id.text2)).setText(subtitle);

                return row;
            }
        };

        mManager.setListener(new UsbSerialPortManager.Listener() {
            @Override
            public void onChanged() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });

        deviceListView.setAdapter(adapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                UsbSerialPort port = (UsbSerialPort) parent.getItemAtPosition(position);
                showConsoleActivity(port);
            }
        });

        File file = new File(Environment.getExternalStorageDirectory() + "/serial-log.txt");
        try {
            file.delete();
            file.createNewFile();
            String[] cmd = new String[] { "logcat", "-f", file.getAbsolutePath(), "-v", "time", "ActivityManager:W", "myapp:D" };
            Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPorts();
    }

    @Override
    protected void onDestroy() {
        UsbSerialPortManager.free();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.device_list_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                refreshPorts();
                return true;
            case R.id.action_about:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void refreshPorts() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            protected Void doInBackground(Void... params) {
                mManager.refreshPorts();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mProgressBar.setVisibility(View.GONE);
            }
        }.execute();
    }

    private void showConsoleActivity(UsbSerialPort port) {
        Intent intent = new Intent(this, ConsoleActivity.class);
        intent.putExtra(ConsoleActivity.EXTRA_USB_DEVICE, port.getDriver().getDevice());
        intent.putExtra(ConsoleActivity.EXTRA_PORT_NUMBER, port.getPortNumber());
        startActivity(intent);
    }
}
