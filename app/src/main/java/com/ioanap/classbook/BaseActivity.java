package com.ioanap.classbook;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.ioanap.classbook.child.ChildProfileActivity;
import com.ioanap.classbook.model.User;
import com.ioanap.classbook.model.UserAccountSettings;
import com.ioanap.classbook.parent.ParentProfileActivity;
import com.ioanap.classbook.teacher.TeacherDrawerActivity;
import com.ioanap.classbook.utils.ChooseUserTypeDialog;

import java.util.concurrent.Executor;

public class BaseActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener,
        ChooseUserTypeDialog.OnUserTypeSelectedListener {

    private static final String TAG = "Base Activity";

    // firebase
    protected FirebaseAuth mAuth;
    protected FirebaseDatabase mFirebaseDatabase;
    protected DatabaseReference mRootRef, mUserRef, mSettingsRef;
    protected String userID;
    protected GoogleApiClient mGoogleApiClient;

    private Context mContext;
    protected ProgressDialog mProgressDialog;

    // signing in mode : facebook | google | email
    private static String MODE = "email";

    // request code for google sign in
    private static final int RC_GOOGLE_SIGN_IN = 2, RC_FACEBOOK_SIGN_IN = 3;
    // Google sign in
    GoogleSignInAccount mGoogleAccount;
    String mUserType; // for users signing in with Google account

    // Facebook
    protected CallbackManager mCallbackManager;
    protected AccessToken mFacebookAccessToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mRootRef = mFirebaseDatabase.getReference();
        mUserRef = mRootRef.child("users");
        mSettingsRef = mRootRef.child("user_account_settings");
        mContext = this;
        mProgressDialog = new ProgressDialog(mContext);

        if(mAuth.getCurrentUser() != null){
            userID = mAuth.getCurrentUser().getUid();
        }

        setupGoogleSignIn();

    }

    // ============= Progress Dialog ===============

    protected void showProgressDialog(String msg) {
        if (mProgressDialog != null && mProgressDialog.isShowing())
            hideProgressDialog();

        mProgressDialog = ProgressDialog.show(this, getResources().getString(R.string.app_name), msg);
    }

    protected void hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    // ====================== Sign Up =========================

    /**
     * Registers new user and on success adds his information to the database and sends a verification
     * link to his email address. The user will be then redirected to the login page.
     *
     * @param user      information to add to the database if register is successful
     * @param email     email for the user that will be registered
     * @param password  password for the user that will be registered
     */
    public void registerNewUser(final User user, String email, String password){
        showProgressDialog("Signing Up...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        mProgressDialog.dismiss();
                        if (task.isSuccessful()) {
                            // save info to firebase
                            FirebaseUser currentUser = task.getResult().getUser();
                            user.setId(currentUser.getUid());
                            addUserInfo(user, new UserAccountSettings());

                            // send verification link to the email address
                            sendEmailVerification();

                            Toast.makeText(mContext, "User registered", Toast.LENGTH_SHORT).show();

                            mAuth.signOut();

                            // redirect to login
                            mContext.startActivity(new Intent(mContext, SignInActivity.class));
                        }
                        else {
                            Toast.makeText(mContext, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }

                    }
                });
    }

    /**
     * Adds data to Firebase for the user with ID user.getId().
     *
     * @param user
     * @param settings
     */
    public void addUserInfo(User user, UserAccountSettings settings) {
        mUserRef.child(user.getId()).setValue(user);

        settings.setId(user.getId());
        settings.setEmail(user.getEmail());
        settings.setUserType(user.getUserType());
        mSettingsRef.child(user.getId()).setValue(settings);
    }

    /**
     * Sends a verification link to the email address of the currently logged user.
     */
    public void sendEmailVerification(){
        final FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if(currentUser != null){
            currentUser.sendEmailVerification()
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if(task.isSuccessful()){
                                Toast.makeText(mContext, "Verification email sent to " + currentUser.getEmail(), Toast.LENGTH_SHORT).show();
                            } else{
                                Toast.makeText(mContext, "Failed to send verification email", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    /**
     * Saves the current state to SharedPreferences (whether there is or not a logged in user and his type)
     *
     * @param logged    tells whether there is a logged in user
     * @param userType  type of the logged in user
     */
    public void saveToSharedPreferences(Boolean logged, String userType) {
        SharedPreferences.Editor editor = mContext.getSharedPreferences("LoginInfo", MODE_PRIVATE).edit();
        editor.putBoolean("logged", logged);
        editor.putString("userType", userType);
        editor.apply();
    }

    // =============== Modify/Retrieve User Data =================

    public UserAccountSettings getUserAccountSettings(DataSnapshot dataSnapshot) {
        Log.d(TAG, "getting user account settings");

        Log.d(TAG, "dataSnapshot: " + dataSnapshot);

        UserAccountSettings settings = dataSnapshot.child(userID).getValue(UserAccountSettings.class);
        Log.d(TAG, "settings: " + settings);

        return settings;
    }

    /**
     * Update "user_account_settings" node for current user.
     *
     * @param name
     * @param description
     * @param location
     * @param phoneNumber
     */
    public void updateUserAccountSettings(String name, String description, String location, String phoneNumber, String profilePhoto) {
        DatabaseReference ref = mSettingsRef.child(userID);

        if (name != null) {
            ref.child(mContext.getString(R.string.field_name)).setValue(name);
        }
        if (description != null) {
            ref.child(mContext.getString(R.string.field_description)).setValue(description);
        }
        if (location != null) {
            ref.child(mContext.getString(R.string.field_location)).setValue(location);
        }
        if (phoneNumber != null) {
            ref.child(mContext.getString(R.string.field_phone_number)).setValue(phoneNumber);
        }
        if (profilePhoto != null) {
            ref.child(mContext.getString(R.string.field_profile_photo)).setValue(profilePhoto);
        }

    }

    /**
     * Receive an image as a byte array, save image to Firebase Storage, get uri(reference) to
     * the image and then call "updateUserAccountSettings" to save reference in Firebase Database.
     *
     * @param bytes
     */
    public void uploadProfilePhoto(byte[] bytes) {
        // add photo to directory in firebase storage
        final StorageReference storageReference = FirebaseStorage.getInstance().getReference()
                .child("photos/" + userID + "/profilePhoto");
        UploadTask uploadTask = storageReference.putBytes(bytes);
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                // get image url
                Uri firebaseUri = taskSnapshot.getDownloadUrl();

                Log.d(TAG, "success - firebase download url: " + firebaseUri.toString());

                // save image url to firebase database
                updateUserAccountSettings(null, null, null, null, firebaseUri.toString());

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(mContext, "Could not upload photo", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ====================== Sign In ==========================

    /**
     * Redirects the currently logged in user to his profile according to his type i.e Teacher,
     * Parent or Child.
     */
    public void userRedirect() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        mUserRef.child(currentUser.getUid()).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        User user = dataSnapshot.getValue(User.class);
                        String userType;

                        if (user == null) {
                            // this is user's first sign in with Google and he doesn't have his data added yet
                            userType = mUserType;
                        } else {
                            userType = user.getUserType();
                        }
                        // save user type to Shared Preferences for future login
                        saveToSharedPreferences(true, userType);

                        // redirect user to corresponding type of profile and delete all previous
                        // activities from stack
                        Intent intent;
                        if (userType.equals("teacher")) {
                            intent = new Intent(mContext, TeacherDrawerActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            mContext.startActivity(intent);
                        } else if (userType.equals("parent")) {
                            intent = new Intent(mContext, ParentProfileActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            mContext.startActivity(intent);
                        } else {
                            intent = new Intent(mContext, ChildProfileActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            mContext.startActivity(intent);
                        }

                        Log.d(TAG, "redirecting as" + userType);

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        Log.e(TAG, "failed to redirect user");
                    }
                });

    }

    public void signIn(String email, String password) {
        showProgressDialog("Signing In...");

        // log in user
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener((Executor) this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        hideProgressDialog();
                        if (task.isSuccessful()) {
                            // signed in with given email and password
                        } else {
                            // toast error message
                            Toast.makeText(mContext, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // ====================== Sign Out ==========================

    /**
     * Signs currently logged user out and clears all activities from stack (except from the first
     * one i.e. SignInActivity)
     */
    public void signOut() {
        // Firebase sign out
        FirebaseAuth.getInstance().signOut();

        // Facebook sign out
        LoginManager.getInstance().logOut();

        // Google sign out
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {

                    }
                });

        // jump to Sign In activity
        Intent intent = new Intent(mContext, SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(intent);
    }

    // ================== Google Sign In =====================

    protected void googleSignIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void setupGoogleSignIn() {
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(BaseActivity.this, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Toast.makeText(getApplicationContext(), "Connection Failed...", Toast.LENGTH_SHORT).show();
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

    }

    /**
     * Checks if email is already in the database
     * If YES, then proceed to sign him in as he has been signed in before
     * If NO, then this is the first sign in (and using Google so add data to the db)
     *
     * @param email
     */
    private void checkFirstGoogleSignIn(String email) {
        // check if there already is an account with this email in the database

        Log.d(TAG, "checkFirstGoogleSignIn for " + email);

        mUserRef
                .orderByChild("email")
                .equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getChildrenCount() > 0) {
                            // we already have a user for this e-mail
                            Log.d(TAG, "checkFirstGoogleSignIn: not first time");

                            firebaseAuthWithGoogle(false);

                        } else {
                            // this is the first sign in for this user
                            // show dialog to select the type of user
                            Log.d(TAG, "checkFirstGoogleSignIn: first time");

                            MODE = "google";
                            ChooseUserTypeDialog dialog = new ChooseUserTypeDialog();
                            dialog.show(getSupportFragmentManager(), "SelectUserType");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        MODE = "email";
                    }
                });
    }

    /**
     * Gets an associated firebase credential for the Google account and signs in with it.
     *
     * @param firstTime if this is the first time user signs in (and with Google) default info will
     *                  be added for him in the database
     */
    private void firebaseAuthWithGoogle(final Boolean firstTime) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + mGoogleAccount.getId());

        AuthCredential credential = GoogleAuthProvider.getCredential(mGoogleAccount.getIdToken(), null);

        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success
                            Log.d(TAG, "signInWithCredential:success");

                            if (firstTime) {
                                // add user default data
                                User user = new User(mGoogleAccount.getEmail(), mUserType);
                                UserAccountSettings settings = new UserAccountSettings();

                                user.setId(mAuth.getCurrentUser().getUid());
                                // get info from Google account
                                settings.setName(mGoogleAccount.getDisplayName());
                                settings.setProfilePhoto(mGoogleAccount.getPhotoUrl().toString());

                                addUserInfo(user, settings);
                            }

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(getApplicationContext(), "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    }
                });
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    /**
     * We get the user type chosen by the new user from the dialog and proceed to sign him in.
     *
     * @param type
     */
    @Override
    public void getUserType(String type) {
        Log.d(TAG, "getUserType: " + type);

        mUserType = type;

        // if result comes from user that signed in for the first time using Google
        if (MODE.equals("google")){
            firebaseAuthWithGoogle(true);
        } else {
            // result comes from user that signed in for the first time using Facebook
            handleFacebookAccessToken(true);
        }

    }

    // ================== Facebook Sign In ==================

    protected void handleFacebookAccessToken(final Boolean firstTime) {
        Log.d(TAG, "handleFacebookAccessToken:" + mFacebookAccessToken);

        AuthCredential credential = FacebookAuthProvider.getCredential(mFacebookAccessToken.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser currentUser = mAuth.getCurrentUser();

                            if (firstTime) {
                                // add user default data
                                User user = new User(currentUser.getEmail(), mUserType);
                                UserAccountSettings settings = new UserAccountSettings();

                                user.setId(mAuth.getCurrentUser().getUid());
                                // get info from Facebook account
                                settings.setName(task.getResult().getUser().getDisplayName());
                                settings.setProfilePhoto(task.getResult().getUser().getPhotoUrl().toString());

                                addUserInfo(user, settings);
                            }

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(BaseActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    /**
     * Checks if email is already in the database
     * If YES, then proceed to sign him in as he has been signed in before
     * If NO, then this is the first sign in (and using Facebook so add data to the db)
     *
     * @param email
     */
    protected void checkFirstFacebookSignIn(String email) {
        // check if there already is an account with this email in the database

        Log.d(TAG, "checkFirstFacebookSignIn for " + email);

        mUserRef
                .orderByChild("email")
                .equalTo(email)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        if (dataSnapshot.getChildrenCount() > 0) {
                            // we already have a user for this e-mail
                            Log.d(TAG, "checkFirstFacebookSignIn: not first time");

                            handleFacebookAccessToken(false);

                        } else {
                            Log.d(TAG, "checkFirstFacebookSignIn: first time");

                            MODE = "facebook";
                            ChooseUserTypeDialog dialog = new ChooseUserTypeDialog();
                            dialog.show(getSupportFragmentManager(), "SelectUserType");
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        MODE = "email";
                    }
                });
    }

    // ========================= Keyboard ===========================

    protected Rect getLocationOnScreen(EditText mEditText) {
        Rect mRect = new Rect();
        int[] location = new int[2];

        mEditText.getLocationOnScreen(location);

        mRect.left = location[0];
        mRect.top = location[1];
        mRect.right = location[0] + mEditText.getWidth();
        mRect.bottom = location[1] + mEditText.getHeight();

        return mRect;
    }

    /**
     * Hide keyboard when tapping outside of it (except from tapping inside an EditText)
     *
     * @param ev
     * @return
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        boolean handleReturn = super.dispatchTouchEvent(ev);

        View view = getCurrentFocus();

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        if(view instanceof EditText){
            View innerView = getCurrentFocus();

            if (ev.getAction() == MotionEvent.ACTION_UP &&
                    !getLocationOnScreen((EditText) innerView).contains(x, y)) {

                InputMethodManager input = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                input.hideSoftInputFromWindow(getWindow().getCurrentFocus()
                        .getWindowToken(), 0);
            }
        }

        return handleReturn;
    }

    // ====================== Icons =====================

    protected void setScaledDrawableButton(Button button, int id, double scale) {
        Drawable drawable = ContextCompat.getDrawable(this, id);
        drawable.setBounds(0, 0, (int) (drawable.getIntrinsicWidth() * scale),
                (int) (drawable.getIntrinsicHeight() * scale));
        button.setCompoundDrawables(drawable, null, null, null);
    }

    // =================== Handle Activity Result ====================

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                mGoogleAccount = result.getSignInAccount();

                checkFirstGoogleSignIn(mGoogleAccount.getEmail());

            } else {
                Toast.makeText(getApplicationContext(), "Sign In with Google Account failed...", Toast.LENGTH_SHORT).show();
            }
        }

        // Result returned
        //if (requestCode == RC_FACEBOOK_SIGN_IN) {
            mCallbackManager.onActivityResult(requestCode, resultCode, data);
        //}

    }

}