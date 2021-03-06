package in.dragonbra.javasteamsamples;

import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails;
import in.dragonbra.javasteam.steam.handlers.steamuser.SteamUser;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.LoginKeyCallback;
import in.dragonbra.javasteam.steam.handlers.steamuser.callback.UpdateMachineAuthCallback;
import in.dragonbra.javasteam.steam.steamclient.SteamClient;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager;
import in.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback;
import in.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback;
import in.dragonbra.javasteam.util.log.DefaultLogListener;
import in.dragonbra.javasteam.util.log.LogManager;

import java.io.*;
import java.util.Scanner;

@SuppressWarnings("Duplicates")
public class SampleSteamGuardRememberMe implements Runnable {

    private SteamClient steamClient;

    private CallbackManager manager;

    private SteamUser steamUser;

    private boolean isRunning;

    private String user;

    private String pass;

    private String authCode;

    private String twoFactorAuth;

    public SampleSteamGuardRememberMe(String user, String pass) {
        this.user = user;
        this.pass = pass;
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Sample1: No username and password specified!");
            return;
        }

        LogManager.addListener(new DefaultLogListener());

        new SampleSteamGuardRememberMe(args[0], args[1]).run();
    }

    @Override
    public void run() {

        // create our steamclient instance
        steamClient = new SteamClient();

        // create the callback manager which will route callbacks to function calls
        manager = new CallbackManager(steamClient);

        // get the steamuser handler, which is used for logging on after successfully connecting
        steamUser = steamClient.getHandler(SteamUser.class);

        // register a few callbacks we're interested in
        // these are registered upon creation to a callback manager, which will then route the callbacks
        // to the functions specified
        manager.subscribe(ConnectedCallback.class, this::onConnected);
        manager.subscribe(DisconnectedCallback.class, this::onDisconnected);

        manager.subscribe(LoggedOnCallback.class, this::onLoggedOn);
        manager.subscribe(LoggedOffCallback.class, this::onLoggedOff);

        manager.subscribe(UpdateMachineAuthCallback.class, this::onMachineAuth);
        manager.subscribe(LoginKeyCallback.class, this::onLoginKey);

        isRunning = true;

        System.out.println("Connecting to steam...");

        // initiate the connection
        steamClient.connect();

        // create our callback handling loop
        while (isRunning) {
            // in order for the callbacks to get routed, they need to be handled by the manager
            manager.runWaitCallbacks(1000L);
        }
    }

    private void onConnected(ConnectedCallback callback) {
        System.out.println("Connected to Steam! Logging in " + user + "...");

        LogOnDetails details = new LogOnDetails();
        details.setUsername(user);

        File loginKeyFile = new File("loginkey.txt");
        if (loginKeyFile.exists()) {
            try (Scanner s = new Scanner(loginKeyFile)) {
                details.setLoginKey(s.nextLine());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            details.setPassword(pass);
        }

        details.setTwoFactorCode(twoFactorAuth);
        details.setAuthCode(authCode);
        details.setShouldRememberPassword(true);

        steamUser.logOn(details);
    }

    private void onDisconnected(DisconnectedCallback callback) {
        System.out.println("Disconnected from Steam, reconnecting in 5...");

        try {
            Thread.sleep(5000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        steamClient.connect();
    }

    private void onLoggedOn(LoggedOnCallback callback) {
        boolean isSteamGuard = callback.getResult() == EResult.AccountLogonDenied;
        boolean is2Fa = callback.getResult() == EResult.AccountLoginDeniedNeedTwoFactor;

        if (isSteamGuard || is2Fa) {
            System.out.println("This account is SteamGuard protected.");

            Scanner s = new Scanner(System.in);
            if (is2Fa) {
                System.out.print("Please enter your 2 factor auth code from your authenticator app: ");
                twoFactorAuth = s.nextLine();
            } else {
                System.out.print("Please enter the auth code sent to the email at " + callback.getEmailDomain());
                authCode = s.nextLine();
            }

            steamClient.disconnect();
            return;
        }
        if (callback.getResult() != EResult.OK) {
            System.out.println("Unable to logon to Steam: " + callback.getResult() + " / " + callback.getExtendedResult());
            isRunning = false;
            steamClient.disconnect();
            return;
        }

        System.out.println("Successfully logged on!");

        // at this point, we'd be able to perform actions on Steam
    }

    private void onLoggedOff(LoggedOffCallback callback) {
        System.out.println("Logged off of Steam: " + callback.getResult());
        isRunning = false;
    }

    private void onMachineAuth(UpdateMachineAuthCallback callback) {
        try (FileOutputStream fos = new FileOutputStream("sentry.bin")) {
            fos.write(callback.getData());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onLoginKey(LoginKeyCallback callback) {
        try (FileWriter fw = new FileWriter("loginkey.txt")) {
            fw.write(callback.getLoginKey());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
