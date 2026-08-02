package com.techy.noti_filter.ui.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.techy.noti_filter.Adapters.ImportantTextAdapter;
import com.techy.noti_filter.databinding.ActivityImportantThingsBinding;

import java.util.ArrayList;
import java.util.List;

public class ImportanceManagerActivity extends AppCompatActivity {

    private ActivityImportantThingsBinding binding;

    private static final int CONTACT_PERMISSION = 100;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityImportantThingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

//Give Permission for contacts

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    CONTACT_PERMISSION);
        } else {
            extractContacts();
        }





        // Important contacts

        binding.ivArrow.setOnClickListener(v -> {

            if (binding.layoutContent.getVisibility() == View.GONE) {

                binding.layoutContent.setVisibility(View.VISIBLE);
                binding.ivArrow.animate().rotation(180f).setDuration(200);

            } else {

                binding.layoutContent.setVisibility(View.GONE);
                binding.ivArrow.animate().rotation(0f).setDuration(200);

            }

        });




// Important text code

        //TODO: Read from shared preferences
        SharedPreferences impTextPrefs = getSharedPreferences("impText", MODE_PRIVATE);
        String impTextString = impTextPrefs.getString("impText", "");


        List<String> impText = new ArrayList<>();
        if (!impTextString.isEmpty()) {
            impText.add(impTextString);
        }
        ImportantTextAdapter adapter = new ImportantTextAdapter(impText);

        binding.impTextRecyclerview.setLayoutManager(
                new LinearLayoutManager(this));

        binding.impTextRecyclerview.setAdapter(adapter);

        binding.addBtn.setOnClickListener(view -> {
            binding.addLayout.setVisibility(VISIBLE);
            binding.addBtn.setVisibility(GONE);
        });

        binding.ivTextArrow.setOnClickListener(v -> {

            if (binding.layoutTextContent.getVisibility() == View.GONE) {

                binding.layoutTextContent.setVisibility(View.VISIBLE);
                binding.ivTextArrow.animate().rotation(180f).setDuration(200);

            } else {

                binding.layoutTextContent.setVisibility(View.GONE);
                binding.ivTextArrow.animate().rotation(0f).setDuration(200);

            }

        });
        binding.addText.setOnClickListener(v -> {

            String text = binding.addTextInput.getText().toString().trim();

            if (!text.isEmpty()) {

                impText.add(text);

                adapter.notifyItemInserted(impText.size() - 1);

                binding.addTextInput.setText("");
            }

        });


    }

    private void extractContacts() {

        List<String> favContacts = new ArrayList<>();
        Cursor cursor = getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.STARRED
                },
                ContactsContract.Contacts.STARRED + " = ?",
                new String[]{"1"},
                ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {

                String id = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));

                String name = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                Log.d("FavoriteContact", id + " : " + name);

                favContacts.add(name);
            }

            SharedPreferences favContactsPrefs = getSharedPreferences("favContacts", MODE_PRIVATE);
            favContactsPrefs.edit().putString("favContacts", String.join(",", favContacts)).apply();


            Log.d("favContacts", favContacts.toString());
            cursor.close();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CONTACT_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                extractContacts();

            } else {
                Toast.makeText(this,
                        "Contacts permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

}