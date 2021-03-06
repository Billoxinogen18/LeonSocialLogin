package am.leon.sociallogin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.snapchat.kit.sdk.SnapLogin;
import com.snapchat.kit.sdk.core.controller.LoginStateController;
import com.snapchat.kit.sdk.login.models.MeData;
import com.snapchat.kit.sdk.login.models.UserDataResponse;
import com.snapchat.kit.sdk.login.networking.FetchUserDataCallback;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;
import com.twitter.sdk.android.core.models.User;

import am.leon.sociallogin.models.FacebookModel;
import am.leon.sociallogin.models.GoogleModel;
import am.leon.sociallogin.models.SnapChatModel;
import am.leon.sociallogin.models.TwitterModel;
import am.leon.sociallogin.response.ProviderType;
import am.leon.sociallogin.response.SocialResponse;
import retrofit2.Call;

public class SocialLogin {

    private View view;
    private Context context;
    private static final int RC_SIGN_IN = 0;
    private SocialLoginCallback loginCallback;

    // Google
    private GoogleSignInClient mGoogleSignInClient;
    // Facebook Callback
    private static CallbackManager callbackManager;
    // Twitter AuthClient
    private static TwitterAuthClient twitterAuthClient;
    // SnapChat
    private MeData snapResult;
    private LoginStateController.OnLoginStateChangedListener mLoginStateChangedListener;


    public SocialLogin(Context context, SocialLoginCallback loginCallback) {
        this.view = ((Activity) context).findViewById(android.R.id.content);
        this.context = context;
        this.loginCallback = loginCallback;

    }


    //----------------------------------------- Facebook -------------------------------------------

    private void facebookInit() {
        callbackManager = CallbackManager.Factory.create();
    }


    public void facebookLogin() {
        facebookInit();
        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        getFacebookInfo(loginResult);
                    }

                    @Override
                    public void onCancel() {
                        failureMessage(context.getString(R.string.canceled_by_user));
                    }

                    @Override
                    public void onError(FacebookException e) {
                        System.out.println(e.getLocalizedMessage());
                        failureMessage(context.getString(R.string.failed_authenticate_please));
                    }
                });
        LoginManager.getInstance().logInWithReadPermissions((Activity) context, SocialGlobalConst.getInstance().getFaceBookPermissions());
    }


    private void getFacebookInfo(LoginResult result) {
        GraphRequest request = GraphRequest.newMeRequest(result.getAccessToken(), ((object, response) -> {
            SocialResponse<FacebookModel> socialResponse = new SocialResponse<>();

            socialResponse.setProviderType(ProviderType.FACEBOOK);
            socialResponse.setResponse(new Gson().fromJson(response.getJSONObject().toString(), new TypeToken<FacebookModel>() {
            }.getType()));

            Log.d("SocialResponse ", socialResponse.toString());

            loginCallback.socialLoginResponse(socialResponse);
        }));

        Bundle parameters = new Bundle();
        parameters.putString("fields", SocialGlobalConst.getInstance().getFaceBookFields());
        request.setParameters(parameters);
        request.executeAsync();
    }


    //----------------------------------------- Twitter --------------------------------------------

    private void twitterInit() {
        TwitterConfig config = new TwitterConfig.Builder(context)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig(context.getString(R.string.twitter_CONSUMER_KEY), context.getString(R.string.twitter_CONSUMER_SECRET)))
                .debug(false)
                .build();
        Twitter.initialize(config);
        twitterAuthClient = new TwitterAuthClient();
    }


    public void twitterLogin() {
        twitterInit();
        PackageManager pkManager = context.getPackageManager();
        try {
            PackageInfo pkgInfo = pkManager.getPackageInfo("com.twitter.android", 0);
            String getPkgInfo = pkgInfo.toString();

            if (!getPkgInfo.equals("com.twitter.android"))
                checkTwitterSession();

        } catch (PackageManager.NameNotFoundException ignored) {
            failureMessage(context.getString(R.string.install_twitter));
        }
    }


    private TwitterSession getTwitterSession() {
        return TwitterCore.getInstance().getSessionManager().getActiveSession();
    }


    private void checkTwitterSession() {
        if (getTwitterSession() == null) {
            twitterAuthClient.authorize((Activity) context, new Callback<TwitterSession>() {
                @Override
                public void success(Result<TwitterSession> result) {
                    TwitterSession twitterSession = result.data;
                    getTwitterData(twitterSession);
                }

                @Override
                public void failure(TwitterException e) {
                    failureMessage(context.getString(R.string.failed_authenticate_please));
                    e.printStackTrace();
                }
            });
        } else
            getTwitterData(getTwitterSession());
    }


    private void getTwitterData(final TwitterSession twitterSession) {
        TwitterApiClient twitterApiClient = new TwitterApiClient(twitterSession);
        final Call<User> getUserCall = twitterApiClient.getAccountService().verifyCredentials(true, false, true);
        getUserCall.enqueue(new Callback<User>() {
            @Override
            public void success(Result<User> result) {
                SocialResponse<TwitterModel> socialResponse = new SocialResponse<>();

                socialResponse.setProviderType(ProviderType.TWITTER);
                socialResponse.setResponse(new Gson().fromJson(new Gson().toJson(result.data), new TypeToken<TwitterModel>() {
                }.getType()));

                Log.d("SocialResponse ", socialResponse.toString());

                loginCallback.socialLoginResponse(socialResponse);
            }

            @Override
            public void failure(TwitterException exception) {
                failureMessage(context.getString(R.string.failed_authenticate_please));
                Log.e("Twitter", "Failed to get user data " + exception.getMessage());
            }
        });
    }


    //----------------------------------------- Google ---------------------------------------------

    private void googleInit() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(context, gso);
    }


    public void googleLogin() {
        googleInit();
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        ((Activity) context).startActivityForResult(signInIntent, RC_SIGN_IN);
    }


    private void handleGoogleResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            SocialResponse<GoogleModel> socialResponse = new SocialResponse<>();

            socialResponse.setProviderType(ProviderType.GOOGLE);
            socialResponse.setResponse(new Gson().fromJson(new Gson().toJson(account), new TypeToken<GoogleModel>() {
            }.getType()));

            Log.d("SocialResponse ", socialResponse.toString());

            loginCallback.socialLoginResponse(socialResponse);

        } catch (ApiException e) {
            failureMessage(context.getString(R.string.failed_authenticate_please));
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCode class reference for more information.
            Log.w("Tag", "sign InResult:failed code =" + e.getStatusCode());
        }
    }


    //----------------------------------------- SnapChat -------------------------------------------


    public void snapChatLogin() {
        SnapLogin.getAuthTokenManager(context).startTokenGrant();

        mLoginStateChangedListener =
                new LoginStateController.OnLoginStateChangedListener() {
                    @Override
                    public void onLoginSucceeded() {
                        Log.d("SnapkitLogin", "Login was successful");
                        getUserDetails();
                    }

                    @Override
                    public void onLoginFailed() {
                        failureMessage(context.getString(R.string.failed_authenticate_please));
                        Log.d("SnapkitLogin", "Login was unsuccessful");
                    }

                    @Override
                    public void onLogout() {
                        Log.d("SnapkitLogin", "User logged out");
                    }
                };

        SnapLogin.getLoginStateController(context).addOnLoginStateChangedListener(mLoginStateChangedListener);
    }


    private void getUserDetails() {
        boolean isUserLoggedIn = SnapLogin.isUserLoggedIn(context);
        if (isUserLoggedIn) {
            Log.d("SnapkitLogin", "The user is logged in");

            SnapLogin.fetchUserData(context, SocialGlobalConst.getInstance().getSnapChatQueries(), null, new FetchUserDataCallback() {
                @Override
                public void onSuccess(@Nullable UserDataResponse userDataResponse) {
                    if (userDataResponse == null || userDataResponse.getData() == null)
                        return;

                    snapResult = userDataResponse.getData().getMe();
                    if (snapResult == null)
                        return;

                    SnapChatModel model = new SnapChatModel();
                    model.setUserID(snapResult.getExternalId());
                    model.setDisplayName(snapResult.getDisplayName());

                    if (snapResult.getBitmojiData() != null)
                        model.setAvatar(snapResult.getBitmojiData().getAvatar());

                    SocialResponse<SnapChatModel> socialResponse = new SocialResponse<>();
                    socialResponse.setProviderType(ProviderType.SNAPCHAT);
                    socialResponse.setResponse(model);

                    Log.d("SocialResponse ", socialResponse.toString());
                    loginCallback.socialLoginResponse(socialResponse);
                }

                @Override
                public void onFailure(boolean isNetworkError, int statusCode) {
                    failureMessage(context.getString(R.string.failed_authenticate_please));
                    Log.d("SnapkitLogin", "No user data fetched " + statusCode);
                }
            });
        }
    }


    public void snapChatLogout() {
        SnapLogin.getLoginStateController(context).removeOnLoginStateChangedListener(mLoginStateChangedListener);
    }


    //----------------------------------------- OnResult -------------------------------------------

    public void onResult(int requestCode, int resultCode, Intent data) {
        System.out.println("from onResult");
        if (FacebookSdk.isFacebookRequestCode(requestCode))
            callbackManager.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TwitterAuthConfig.DEFAULT_AUTH_REQUEST_CODE) {
            // Pass Twitter result to the twitterAuthClient.
            if (twitterAuthClient != null)
                twitterAuthClient.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == RC_SIGN_IN) {
            // This Task Returned from this call is always completed , no need to attach a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleResult(task);
        }
    }


//    private static Bitmap getFacebookProfilePicture(String picLink) {
//        Bitmap bitmap = null;
//
////            String link = "https://graph.facebook.com/" + userID + "/picture?type=large";
//        if (android.os.Build.VERSION.SDK_INT > 8) {
//            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
//                    .permitAll().build();
//            StrictMode.setThreadPolicy(policy);
//            try {
//                URL imageURL = new URL(picLink);
//                bitmap = BitmapFactory.decodeStream(imageURL.openConnection().getInputStream());
//            } catch (IOException ignored) {
//            }
//            return bitmap;
//        } else
//            return null;
//    }

    private void failureMessage(String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }


    public interface SocialLoginCallback {
        void socialLoginResponse(SocialResponse<?> response);
    }

}