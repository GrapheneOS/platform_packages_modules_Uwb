/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ranging.rangingtestapp;

import android.Manifest.permission;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        NavHostFragment navHostFragment =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        NavController navController = navHostFragment.getNavController();
        NavigationView navView = findViewById(R.id.nav_view);

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_initiator,
                R.id.navigation_responder,
                R.id.navigation_ios_accessory)
                .setOpenableLayout(drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        View navHostView = findViewById(R.id.nav_host_fragment);
        View configContainer = findViewById(R.id.config_fragment_container);
        View logContainer = findViewById(R.id.log_fragment_container);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                CharSequence tabText = tab.getText();
                if (tabText == null) return;

                if (tabText.equals("Session")) {
                    navHostView.setVisibility(View.VISIBLE);
                    configContainer.setVisibility(View.GONE);
                    logContainer.setVisibility(View.GONE);
                } else if (tabText.equals("Config")) {
                    navHostView.setVisibility(View.GONE);
                    configContainer.setVisibility(View.VISIBLE);
                    logContainer.setVisibility(View.GONE);
                } else if (tabText.equals("Logs")) {
                    navHostView.setVisibility(View.GONE);
                    configContainer.setVisibility(View.GONE);
                    logContainer.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destinationId = destination.getId();
            Fragment configFragment = null;
            if (destinationId == R.id.navigation_initiator) {
                configFragment = new InitiatorConfigurationFragment();
            } else if (destinationId == R.id.navigation_responder) {
                configFragment = new ResponderConfigurationFragment();
            }

            if (configFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.config_fragment_container, configFragment)
                        .commit();
                if (tabLayout.getTabCount() == 2) {
                    tabLayout.addTab(tabLayout.newTab().setText("Config"), 1);
                }
            } else {
                if (tabLayout.getTabCount() == 3) {
                    tabLayout.removeTabAt(1);
                }
            }
        });

        requestPermissions();
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = ((NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment)).getNavController();
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    private void requestPermissions() {
        String[] requiredPermissions =
                new String[] {
                    permission.ACCESS_COARSE_LOCATION,
                    permission.ACCESS_FINE_LOCATION,
                    permission.BLUETOOTH_ADVERTISE,
                    permission.BLUETOOTH_CONNECT,
                    permission.BLUETOOTH_SCAN,
                    permission.RANGING
                };
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    MainActivity.this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                finish();
            }
        }
    }
}
