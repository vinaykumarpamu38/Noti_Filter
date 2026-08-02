package com.techy.noti_filter.ui.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.techy.noti_filter.R;
import com.techy.noti_filter.service.NotificationSuppressionPolicy;
import com.techy.noti_filter.ui.activities.AccuracyHistoryActivity;
import com.techy.noti_filter.ui.activities.ImportanceManagerActivity;

import java.io.FileInputStream;
import java.io.IOException;

public class ProfileFragment extends Fragment {

    private TextView usernameEditText;

    private LinearLayout importantThings;
    private TextView emailEditText;
    private ImageView profileImage;
    private FloatingActionButton editProfileButton;

//    private AppDatabase db;
//    private ProfileDao profileDao;
//    private UserDao userDao;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize views
        profileImage = view.findViewById(R.id.profile_image);
        usernameEditText = view.findViewById(R.id.username_text);
        emailEditText = view.findViewById(R.id.email_text);
        importantThings = view.findViewById(R.id.important_Things);

        importantThings.setOnClickListener(v->{
            startActivity(new Intent(requireContext(), ImportanceManagerActivity.class));
        });

        View accuracyHistory = view.findViewById(R.id.accuracy_history);
        accuracyHistory.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AccuracyHistoryActivity.class)));


        com.google.android.material.materialswitch.MaterialSwitch suppressionSwitch =
                view.findViewById(R.id.suppression_switch);
        suppressionSwitch.setChecked(
                NotificationSuppressionPolicy.isEnabled(requireContext()));
        suppressionSwitch.setOnCheckedChangeListener((btn, isChecked) ->
                NotificationSuppressionPolicy.setEnabled(requireContext(), isChecked));


        // Start a new thread to fetch data
//        new Thread(() -> {
//            db = AppDatabase.getInstance(requireContext());
//            profileDao = db.profileDao();
//            userDao = db.userDao();
//            String userEmail = getUser();
//            User userByEmail = userDao.getUserByEmail(userEmail);
//            Profile profilesForUser = profileDao.getProfilesForUser(userByEmail.getId());
//
//            // Update UI with fetched data
//            logIn(profilesForUser, userByEmail);
//        }).start(); // Start the thread to fetch data

//        editProfileButton = view.findViewById(R.id.Edit_Profile_FAB);
//        editProfileButton.setOnClickListener(v -> {
//            // Handle profile edit here
//            Toast.makeText(getActivity(), "Edit Profile Clicked", Toast.LENGTH_SHORT).show();
//            // Replace the current fragment with the EditProfileFragment
//            getParentFragmentManager().beginTransaction()
//                    .replace(R.id.frame_layout, new EditProfileFragment())
//                    .addToBackStack(null)
//                    .commit();
//        });

        return view;
    }

//    private void logIn(Profile profilesForUser, User userByEmail) {
//        if (profilesForUser != null) {
//            // Update UI on the main thread
//            requireActivity().runOnUiThread(() -> {
//                usernameEditText.setText(userByEmail.getUserName());
//                emailEditText.setText(userByEmail.getEmail());
//                stepsGoalEditText.setText(profilesForUser.getStepGoal()+" steps");
//                distanceGoalEditText.setText(profilesForUser.getDistanceGoal()+" km");
//                caloriesGoalEditText.setText(profilesForUser.getCaloriesGoal()+" cal");
//                weightGoalEditText.setText(profilesForUser.getWeightGoal()+" kg");
//                actualWeight.setText("Actual Weight: " + profilesForUser.getWeight() + " kg");
//                gender.setText("Gender: " + profilesForUser.getGender());
//                height.setText("Height: " + profilesForUser.getHeight() + " cm");
//
//                if (profilesForUser.getProfileImageFileName() != null) {
//                    Bitmap bitmap = loadProfileImage(profilesForUser.getProfileImageFileName());
//                    if (bitmap != null) {
//                        profileImage.setImageBitmap(bitmap);
//                    }
//                }
//            });
//        }
//    }

    private Bitmap loadProfileImage(String fileName) {
        try {
            FileInputStream fis = requireContext().openFileInput(fileName);
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

//    private String getUser() {
//        SharedPreferences sp = getActivity().getSharedPreferences("userJWT", MODE_PRIVATE);
//        String jwt = sp.getString("jwt", null);
//        TokenUtils token = new TokenUtils();
//        return token.getUserIdFromToken(jwt);
//    }
}
