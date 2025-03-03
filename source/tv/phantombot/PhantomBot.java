/*
 * Copyright (C) 2016-2021 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package tv.phantombot;

import com.gmt2001.GamesListUpdater;
import com.gmt2001.HttpRequest;
import com.gmt2001.RollbarProvider;
import com.gmt2001.TwitchAPIv5;
import com.gmt2001.TwitchAuthorizationCodeFlow;
import com.gmt2001.TwitchClientCredentialsFlow;
import com.gmt2001.YouTubeAPIv3;
import com.gmt2001.datastore.DataStore;
import com.gmt2001.datastore.DataStoreConverter;
import com.gmt2001.datastore.H2Store;
import com.gmt2001.datastore.MySQLStore;
import com.gmt2001.datastore.SqliteStore;
import com.gmt2001.eventsub.EventSub;
import com.gmt2001.httpwsserver.HTTPWSServer;
import com.illusionaryone.GitHubAPIv3;
import com.illusionaryone.TwitchAlertsAPIv1;
import com.illusionaryone.TwitterAPI;
import com.scaniatv.CustomAPI;
import com.scaniatv.StreamElementsAPIv2;
import com.scaniatv.TipeeeStreamAPIv1;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.engio.mbassy.listener.Handler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.SystemUtils;
import org.json.JSONException;
import reactor.util.Loggers;
import tv.phantombot.cache.DonationsCache;
import tv.phantombot.cache.EmotesCache;
import tv.phantombot.cache.FollowersCache;
import tv.phantombot.cache.StreamElementsCache;
import tv.phantombot.cache.TipeeeStreamCache;
import tv.phantombot.cache.TwitchCache;
import tv.phantombot.cache.TwitchTeamsCache;
import tv.phantombot.cache.UsernameCache;
import tv.phantombot.cache.ViewerListCache;
import tv.phantombot.console.ConsoleEventHandler;
import tv.phantombot.console.ConsoleInputListener;
import tv.phantombot.discord.DiscordAPI;
import tv.phantombot.event.EventBus;
import tv.phantombot.event.Listener;
import tv.phantombot.event.command.CommandEvent;
import tv.phantombot.event.irc.channel.IrcChannelUserModeEvent;
import tv.phantombot.event.irc.complete.IrcJoinCompleteEvent;
import tv.phantombot.event.irc.message.IrcChannelMessageEvent;
import tv.phantombot.event.irc.message.IrcPrivateMessageEvent;
import tv.phantombot.event.jvm.ShutdownEvent;
import tv.phantombot.httpserver.HTTPAuthenticatedHandler;
import tv.phantombot.httpserver.HTTPNoAuthHandler;
import tv.phantombot.httpserver.HTTPOAuthHandler;
import tv.phantombot.httpserver.HTTPPanelAndYTHandler;
import tv.phantombot.panel.WsAlertsPollsHandler;
import tv.phantombot.panel.WsPanelHandler;
import tv.phantombot.panel.WsPanelRemoteLoginHandler;
import tv.phantombot.script.Script;
import tv.phantombot.script.ScriptEventManager;
import tv.phantombot.script.ScriptFileWatcher;
import tv.phantombot.script.ScriptManager;
import tv.phantombot.scripts.core.Moderation;
import tv.phantombot.twitch.api.Helix;
import tv.phantombot.twitch.api.TwitchValidate;
import tv.phantombot.twitch.irc.TwitchSession;
import tv.phantombot.twitch.irc.host.TwitchWSHostIRC;
import tv.phantombot.twitch.pubsub.TwitchPubSub;
import tv.phantombot.ytplayer.WsYTHandler;

public final class PhantomBot implements Listener {

    /* Bot Information */
    private String botName;
    private String channelName;
    private String ownerName;
    private String oauth;
    private String apiOAuth;
    private static Double messageLimit;
    private static Double whisperLimit;
    private TwitchAuthorizationCodeFlow authflow;
    private TwitchClientCredentialsFlow appflow;

    /* Web Information */
    private String panelUsername;
    private String panelPassword;
    private String webOAuth;
    private String webOAuthThro;
    private String youtubeOAuth;
    private String youtubeOAuthThro;
    private String youtubeKey;
    private boolean webEnabled;
    private boolean musicEnabled;
    private boolean useHttps;
    private int basePort;
    private String bindIP;

    /* SSL information */
    private String httpsPassword = "";
    private String httpsFileName = "";

    /* DataStore Information */
    private DataStore dataStore;
    private String dataStoreType;
    private String dataStoreConfig;

    /* MySQL Information */
    private String mySqlConn;
    private String mySqlHost;
    private String mySqlPort;
    private String mySqlName;
    private String mySqlUser;
    private String mySqlPass;

    /* Twitter Information */
    private String twitterUsername;
    private String twitterAccessToken;
    private String twitterSecretToken;
    private String twitterConsumerSecret;
    private String twitterConsumerToken;

    /* TwitchAlerts Information */
    private String twitchAlertsKey = "";
    private int twitchAlertsLimit = 0;

    /* TipeeeStream Information */
    private String tipeeeStreamOAuth = "";
    private int tipeeeStreamLimit = 5;

    /* StreamElements Information */
    private String streamElementsJWT = "";
    private String streamElementsID = "";
    private int streamElementsLimit = 5;

    /* Discord Configuration */
    private String discordToken = "";

    /* Caches */
    private FollowersCache followersCache;
    private EmotesCache emotesCache;
    private TwitchCache twitchCache;
    private TwitchTeamsCache twitchTeamCache;
    private TipeeeStreamCache tipeeeStreamCache;
    private ViewerListCache viewerListCache;
    private StreamElementsCache streamElementCache;
    public static String twitchCacheReady = "false";

    /* Sockets */
    private WsAlertsPollsHandler alertsPollsHandler;
    private WsPanelHandler panelHandler;
    private WsYTHandler ytHandler;
    private HTTPOAuthHandler oauthHandler;

    /* PhantomBot Information */
    private static PhantomBot instance;
    private static boolean reloadScripts = false;
    private static boolean silentScriptsLoad = false;
    private static boolean enableDebugging = false;
    private static boolean enableDebuggingLogOnly = false;
    private static boolean enableRhinoDebugger = false;
    private static boolean enableRhinoES6 = false;
    private static String timeZone = "GMT";
    private static boolean twitchTcpNodelay = true;
    private static boolean isInExitState = false;
    private boolean isExiting = false;
    private boolean interactive;

    /* Other Information */
    private boolean newSetup = false;
    private TwitchSession session;
    private SecureRandom random;
    private boolean joined = false;
    private TwitchWSHostIRC wsHostIRC;
    private TwitchPubSub pubSubEdge;
    private CaselessProperties pbProperties;
    private boolean backupDBAuto = false;
    private int backupDBHourFrequency = 0;
    private int backupDBKeepDays = 0;

    // Error codes
    // [...] by convention, a nonzero status code indicates abnormal termination. (see System.exit() JavaDoc)
    private static final int EXIT_STATUS_OK = 0;
    private static final int EXIT_STATUS_ERROR = 1;

    /**
     * PhantomBot Instance.
     *
     * @return PhantomBot The current instance of PhantomBot
     */
    public static PhantomBot instance() {
        return instance;
    }

    /**
     * Current Repo Of PhantomBot.
     *
     * @return String The current GitHub repository version of PhantomBot.
     */
    public String repoVersion() {
        return RepoVersion.getRepoVersion();
    }

    /**
     * Current Version Of PhantomBot.
     *
     * @return String Display version of PhantomBot.
     */
    public String botVersion() {
        return "PhantomBot Version: " + RepoVersion.getPhantomBotVersion() + " (" + RepoVersion.getBuildType() + (RepoVersion.isDocker() ? ", Docker" : "") + ")";
    }

    /**
     * Used by the panel on the informations tab.
     *
     * @return String PhantomBot information for the Panel.
     */
    public String getBotInfo() {
        return botVersion() + " (Revision: " + repoVersion() + ")";
    }

    /**
     * Current Build Revision
     *
     * @return String The build revision of PhantomBot.
     */
    public String botRevision() {
        return "Build Revision: " + repoVersion();
    }

    /**
     * Only used on bot boot up for now.
     *
     * @return {string} bot creator
     */
    public String getBotCreator() {
        return "Creator: mast3rplan";
    }

    /**
     * Only used on bot boot up for now.
     *
     * @return {string} bot developers
     */
    public String botDevelopers() {
        return "Developers: gmt2001, Kojitsari, ScaniaTV, & IllusionaryOne";
    }

    /**
     * Only used on bot boot up for now.
     *
     * @return {string} bot website
     */
    public String getWebSite() {
        return "https://phantombot.github.io/PhantomBot/";
    }

    /**
     * Prints a message in the bot console.
     *
     * @param {Object} message
     */
    private void print(String message) {
        com.gmt2001.Console.out.println(message);
    }

    /**
     * Checks port availability.
     *
     * @param {int} port
     */
    public void checkPortAvailabity(int port) {
        ServerSocket serverSocket = null;
        try {
            serverSocket = bindIP.isEmpty() ? new ServerSocket(port) : new ServerSocket(port, 1, java.net.InetAddress.getByName(bindIP));
            serverSocket.setReuseAddress(true);
        } catch (IOException e) {
            com.gmt2001.Console.err.println("Port is already in use: " + port);
            com.gmt2001.Console.err.println("Ensure that another copy of PhantomBot is not running.");
            com.gmt2001.Console.err.println("If another copy is not running, try to change baseport in ./config/botlogin.txt");
            com.gmt2001.Console.err.println("PhantomBot will now exit.");
            PhantomBot.exitError();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    com.gmt2001.Console.err.println("Unable to close port for testing: " + port);
                    com.gmt2001.Console.err.println("PhantomBot will now exit.");
                    PhantomBot.exitError();
                }
            }
        }
    }

    /**
     * Check to see if YouTube Key is configured.
     */
    public boolean isYouTubeKeyEmpty() {
        return youtubeKey.isEmpty();
    }

    /**
     * Constructor for PhantomBot object.
     *
     * @param Properties Properties object which configures the PhantomBot instance.
     */
    public PhantomBot(CaselessProperties pbProperties) {
        if (pbProperties.getPropertyAsBoolean("reactordebug", false)) {
            Loggers.useVerboseConsoleLoggers();
        }

        /* Set the default bot variables */
        PhantomBot.enableDebugging = pbProperties.getPropertyAsBoolean("debugon", false);

        if (pbProperties.getPropertyAsBoolean("userollbar", true)) {
            RollbarProvider.instance().enable();
        }

        /* Set the exeption handler */
        Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());

        /* Start loading the bot information */
        print("");
        print(botVersion());
        print(botRevision());
        print(getBotCreator());
        print(botDevelopers());
        print(getWebSite());
        print("");

        /* System interactive */
        interactive = (System.getProperty("interactive") != null);

        this.authflow = new TwitchAuthorizationCodeFlow(pbProperties.getProperty("clientid"), pbProperties.getProperty("clientsecret"));
        this.appflow = new TwitchClientCredentialsFlow(pbProperties.getProperty("clientid"), pbProperties.getProperty("clientsecret"));
        boolean authflowrefreshed = this.authflow.checkAndRefreshTokens(pbProperties);
        boolean appflowrefreshed = this.appflow.checkExpirationAndGetNewToken(pbProperties);
        if (authflowrefreshed || appflowrefreshed) {
            pbProperties = ConfigurationManager.getConfiguration();
        }

        /* Assign properties passed in to local instance. */
        this.pbProperties = pbProperties;
        this.botName = this.pbProperties.getProperty("user").toLowerCase();
        this.channelName = this.pbProperties.getProperty("channel").toLowerCase();
        this.ownerName = this.pbProperties.getProperty("owner").toLowerCase();
        this.apiOAuth = this.pbProperties.getProperty("apioauth", "");
        this.oauth = this.pbProperties.getProperty("oauth");

        /* Set the web variables */
        this.youtubeOAuth = this.pbProperties.getProperty("ytauth");
        this.youtubeOAuthThro = this.pbProperties.getProperty("ytauthro");
        this.youtubeKey = this.pbProperties.getProperty("youtubekey", "");
        this.basePort = this.pbProperties.getPropertyAsInt("baseport", 25000);
        this.bindIP = this.pbProperties.getProperty("bindIP", "");
        this.webOAuth = this.pbProperties.getProperty("webauth");
        this.webOAuthThro = this.pbProperties.getProperty("webauthro");
        this.webEnabled = this.pbProperties.getPropertyAsBoolean("webenable", true);
        this.musicEnabled = this.pbProperties.getPropertyAsBoolean("musicenable", true);
        this.useHttps = this.pbProperties.getPropertyAsBoolean("usehttps", true);

        /* Set the datastore variables */
        this.dataStoreType = this.pbProperties.getProperty("datastore", "");
        this.dataStoreConfig = this.pbProperties.getProperty("datastoreconfig", "");

        /* Set the Twitter variables */
        this.twitterUsername = this.pbProperties.getProperty("twitterUser", "");
        this.twitterConsumerToken = this.pbProperties.getProperty("twitter_consumer_key", "");
        this.twitterConsumerSecret = this.pbProperties.getProperty("twitter_consumer_secret", "");
        this.twitterAccessToken = this.pbProperties.getProperty("twitter_access_token", "");
        this.twitterSecretToken = this.pbProperties.getProperty("twitter_secret_token", "");

        /* Set the Discord variables */
        this.discordToken = this.pbProperties.getProperty("discord_token", "");

        /* Set the TwitchAlerts variables */
        this.twitchAlertsKey = this.pbProperties.getProperty("twitchalertskey", "");
        this.twitchAlertsLimit = this.pbProperties.getPropertyAsInt("twitchalertslimit", 5);

        /* Set the TipeeeStream variables */
        this.tipeeeStreamOAuth = this.pbProperties.getProperty("tipeeestreamkey", "");
        this.tipeeeStreamLimit = this.pbProperties.getPropertyAsInt("tipeeestreamlimit", 5);

        /* Set the StreamElements variables */
        this.streamElementsJWT = this.pbProperties.getProperty("streamelementsjwt", "");
        this.streamElementsID = this.pbProperties.getProperty("streamelementsid", "");
        this.streamElementsLimit = this.pbProperties.getPropertyAsInt("streamelementslimit", 5);

        /* Set the MySql variables */
        this.mySqlName = this.pbProperties.getProperty("mysqlname", "");
        this.mySqlUser = this.pbProperties.getProperty("mysqluser", "");
        this.mySqlPass = this.pbProperties.getProperty("mysqlpass", "");
        this.mySqlHost = this.pbProperties.getProperty("mysqlhost", "");
        this.mySqlPort = this.pbProperties.getProperty("mysqlport", "");

        /* twitch cache */
        PhantomBot.twitchCacheReady = "false";

        /* Set the SSL info */
        this.httpsFileName = this.pbProperties.getProperty("httpsFileName", "");
        this.httpsPassword = this.pbProperties.getProperty("httpsPassword", "");

        /* Set the timeZone */
        PhantomBot.timeZone = this.pbProperties.getProperty("logtimezone", "GMT");

        /* Set the panel username login for the panel to use */
        this.panelUsername = this.pbProperties.getProperty("paneluser", "panel");

        /* Set the panel password login for the panel to use */
        this.panelPassword = this.pbProperties.getProperty("panelpassword", "panel");

        /* Set the tcp delay toggle. Having this set to true uses a bit more bandwidth but sends messages to Twitch faster. */
        PhantomBot.twitchTcpNodelay = this.pbProperties.getPropertyAsBoolean("twitch_tcp_nodelay", true);


        /*
         * Set the message limit for session.java to use, note that Twitch rate limits at 100 messages in 30 seconds
         * for moderators.  For non-moderators, the maximum is 20 messages in 30 seconds. While it is not recommended
         * to go above anything higher than 19 in case the bot is ever de-modded, the option is available but is
         * capped at 100.0.
         */
        PhantomBot.messageLimit = Math.floor(this.pbProperties.getPropertyAsDouble("msglimit30", 19.0));
        if (PhantomBot.messageLimit > 99.0) {
            PhantomBot.messageLimit = 99.0;
        } else if (PhantomBot.messageLimit < 19.0) {
            PhantomBot.messageLimit = 19.0;
        }

        /* Set the whisper limit for session.java to use. -- Currently Not Used -- */
        PhantomBot.whisperLimit = this.pbProperties.getPropertyAsDouble("whisperlimit60", 60.0);

        /* Set any DB backup options. */
        this.backupDBAuto = this.pbProperties.getPropertyAsBoolean("backupdbauto", this.pbProperties.getPropertyAsBoolean("backupsqliteauto", true));
        this.backupDBHourFrequency = this.pbProperties.getPropertyAsInt("backupdbhourfrequency", this.pbProperties.getPropertyAsInt("backupsqlitehourfrequency", 24));
        this.backupDBKeepDays = this.pbProperties.getPropertyAsInt("backupdbkeepdays", this.pbProperties.getPropertyAsInt("backupsqlitekeepdays", 5));

        // Set the newSetup flag
        this.newSetup = this.pbProperties.getPropertyAsBoolean("newSetup", false);

        /* Load up a new SecureRandom for the scripts to use */
        random = new SecureRandom();

        /* Load the datastore */
        if (dataStoreType.isBlank() && System.getProperty("os.name").toLowerCase().endsWith("bsd")) {
            dataStoreType = "h2store";
        }

        if (dataStoreType.equalsIgnoreCase("mysqlstore")) {
            if (this.mySqlPort.isEmpty()) {
                this.mySqlConn = "jdbc:mysql://" + this.mySqlHost + "/" + this.mySqlName + "?useSSL=false&user=" + this.mySqlUser + "&password=" + this.mySqlPass;
            } else {
                this.mySqlConn = "jdbc:mysql://" + this.mySqlHost + ":" + this.mySqlPort + "/" + this.mySqlName + "?useSSL=false&user=" + this.mySqlUser + "&password=" + this.mySqlPass;
            }

            dataStore = MySQLStore.instance(this.mySqlConn);

            /* Check to see if we can create a connection */
            if (!dataStore.CanConnect(this.mySqlConn, this.mySqlUser, this.mySqlPass)) {
                print("Could not create a connection with MySQL Server. PhantomBot now shutting down...");
                PhantomBot.exitError();
            }
            /* Convert to MySql */
            if (SqliteStore.hasDatabase(dataStoreConfig) && SqliteStore.instance().GetFileList().length > 0 && MySQLStore.instance().GetFileList().length == 0) {
                DataStoreConverter.convertDataStore(MySQLStore.instance(), SqliteStore.instance());
            }
        } else if (dataStoreType.equalsIgnoreCase("h2store")) {
            dataStore = H2Store.instance(dataStoreConfig);

            if (!dataStore.CanConnect()) {
                print("Could not create a connection with H2 Database. PhantomBot now shutting down...");
                PhantomBot.exitError();
            }

            if (SqliteStore.hasDatabase(dataStoreConfig) && SqliteStore.instance().GetFileList().length > 0 && H2Store.instance().GetFileList().length == 0) {
                DataStoreConverter.convertDataStore(H2Store.instance(), SqliteStore.instance());
            }
        } else {
            dataStoreType = "sqlite3store";
            dataStore = SqliteStore.instance(dataStoreConfig);

            /* Handle index operations. */
            com.gmt2001.Console.debug.println("Checking database indexes, please wait...");
            dataStore.CreateIndexes();
        }

        /* Set the oauth key in the Twitch api and perform a validation. */
        if (!this.apiOAuth.isEmpty()) {
            Helix.instance().setOAuth(this.apiOAuth);
            TwitchValidate.instance().validateAPI(this.apiOAuth, "API (apioauth)");
        }

        /* Validate the chat OAUTH token. */
        TwitchValidate.instance().validateChat(this.oauth, "CHAT (oauth)");

        TwitchValidate.instance().checkOAuthInconsistencies(this.botName);

        if (pbProperties.getPropertyAsBoolean("useeventsub", false)) {
            TwitchValidate.instance().validateApp(pbProperties.getProperty("apptoken"), "APP (EventSub)");
        }

        /* Set the TwitchAlerts OAuth key and limiter. */
        if (!twitchAlertsKey.isEmpty()) {
            TwitchAlertsAPIv1.instance().SetAccessToken(twitchAlertsKey);
            TwitchAlertsAPIv1.instance().SetDonationPullLimit(twitchAlertsLimit);
        }

        /* Set the YouTube API Key if provided. */
        if (!this.youtubeKey.isEmpty()) {
            YouTubeAPIv3.instance().SetAPIKey(this.youtubeKey);
        }

        /* Set the TipeeeStream oauth key. */
        if (!tipeeeStreamOAuth.isEmpty()) {
            TipeeeStreamAPIv1.instance().SetOauth(tipeeeStreamOAuth);
            TipeeeStreamAPIv1.instance().SetLimit(tipeeeStreamLimit);
        }

        /* Set the StreamElements JWT token. */
        if (!streamElementsJWT.isEmpty() && !streamElementsID.isEmpty()) {
            StreamElementsAPIv2.instance().SetJWT(streamElementsJWT);
            StreamElementsAPIv2.instance().SetID(streamElementsID);
            StreamElementsAPIv2.instance().SetLimit(streamElementsLimit);
        }

        /* Start things and start loading the scripts. */
        this.init();

        /* Check if the OS is Linux. */
        if (SystemUtils.IS_OS_LINUX && !interactive) {
            try {
                java.lang.management.RuntimeMXBean runtime = java.lang.management.ManagementFactory.getRuntimeMXBean();
                int pid = Integer.parseInt(runtime.getName().split("@")[0]);

                File file = new File("PhantomBot." + this.botName + ".pid");

                try (FileOutputStream fs = new FileOutputStream(file, false)) {
                    PrintStream ps = new PrintStream(fs);
                    ps.print(pid);
                }
                file.deleteOnExit();
            } catch (SecurityException | IllegalArgumentException | IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }
        }

        if (!TwitchValidate.instance().isChatValid()) {
            com.gmt2001.Console.warn.println();
            com.gmt2001.Console.warn.println("OAuth was invalid, not starting TMI (Chat)");
            com.gmt2001.Console.warn.println("Please go the the bots built-in oauth page and setup a new Bot (Chat) token");
            com.gmt2001.Console.warn.println("The default URL is http://localhost:25000/oauth/");
            com.gmt2001.Console.warn.println("Please restart the bot after setting up OAuth");
            com.gmt2001.Console.warn.println();
        } else {
            /* Start a session instance and then connect to WS-IRC @ Twitch. */
            this.session = new TwitchSession(this.channelName, this.botName, this.oauth).connect();

            /* Start a host checking instance. */
            if (apiOAuth.length() > 0 && checkModuleEnabled("./handlers/hostHandler.js")) {
                this.wsHostIRC = new TwitchWSHostIRC(this.channelName, this.apiOAuth);
            }
        }
    }

    /**
     * Tells you if the build is a nightly.
     *
     * @return {boolean}
     */
    public boolean isNightly() {
        return RepoVersion.getNightlyBuild();
    }

    /**
     * Tells you if the build is a pre-release.
     *
     * @return {boolean}
     */
    public boolean isPrerelease() {
        return RepoVersion.getPrereleaseBuild();
    }

    public TwitchAuthorizationCodeFlow getAuthFlow() {
        return this.authflow;
    }

    public TwitchClientCredentialsFlow getAppFlow() {
        return this.appflow;
    }

    public void reconnect() {
        if (this.session != null) {
            this.session.reconnect();
        }
        if (this.wsHostIRC != null) {
            this.wsHostIRC.reconnect();
        }
        if (this.pubSubEdge != null) {
            this.pubSubEdge.reconnect(true);
        }
    }

    public void reloadProperties() {
        this.apiOAuth = this.pbProperties.getProperty("apioauth", "");
        this.oauth = this.pbProperties.getProperty("oauth");
        Helix.instance().setOAuth(this.apiOAuth);
        if (this.session != null) {
            this.session.setOAuth(this.oauth);
        }
        if (this.wsHostIRC != null) {
            this.wsHostIRC.setOAuth(this.apiOAuth);
        }
        if (this.pubSubEdge != null) {
            this.pubSubEdge.setOAuth(this.apiOAuth);
        }
    }

    public static String GetExecutionPath() {
        try {
            return Paths.get(PhantomBot.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().toAbsolutePath().toRealPath().toString();
        } catch (IOException | URISyntaxException ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        return ".";
    }

    /**
     * Enables or disables the debug mode.
     *
     * @param {boolean} debug
     */
    public static void setDebugging(boolean debug) {
        if (debug) {
            com.gmt2001.Console.out.println("Debug Mode Enabled");
        }
        PhantomBot.enableDebugging = debug;
    }

    /**
     * Enables or disables log only debug mode.
     *
     * @param {boolean} debug
     */
    public static void setDebuggingLogOnly(boolean debug) {
        if (debug) {
            com.gmt2001.Console.out.println("Debug Log Only Mode Enabled");
        }
        PhantomBot.enableDebugging = debug;
        PhantomBot.enableDebuggingLogOnly = debug;
    }

    /**
     * Tells you the bot name.
     *
     * @return {string} bot name
     */
    public String getBotName() {
        return this.botName;
    }

    public HTTPOAuthHandler getHTTPOAuthHandler() {
        return this.oauthHandler;
    }

    /**
     * Gives you the current data store
     *
     * @return {datastore} dataStore
     */
    public DataStore getDataStore() {
        return this.dataStore;
    }

    /**
     * Tells you if the bot is exiting
     *
     * @return {boolean} exit
     */
    public boolean isExiting() {
        return this.isExiting;
    }

    /**
     * Give's you the channel for that channelName.
     *
     * @return {channel}
     */
    public String getChannelName() {
        return this.channelName;
    }

    /**
     * Tells you if the discord token has been set.
     *
     * @return {boolean}
     */
    public boolean hasDiscordToken() {
        return this.discordToken.isEmpty();
    }

    /**
     * Give's you the session for that channel.
     *
     * @return {session}
     */
    public TwitchSession getSession() {
        return this.session;
    }

    /**
     * Method that returns the message limit
     *
     * @return {double} messageLimit
     */
    public static double getMessageLimit() {
        return messageLimit;
    }

    /**
     * Give's you the message limit.
     *
     * @return {long} message limit
     */
    public static long getMessageInterval() {
        return (long) ((30.0 / messageLimit) * 1000);
    }

    /**
     * Give's you the whisper limit. *Currently not used*
     *
     * @return {long} whisper limit
     */
    public static long getWhisperInterval() {
        return (long) ((60.0 / whisperLimit) * 1000);
    }

    /**
     * Helper method to see if a module is enabled.
     *
     * @param String Module name to check for
     * @return boolean If the module is enabled or not
     */
    public boolean checkModuleEnabled(String module) {
        try {
            return dataStore.GetString("modules", "", module).equals("true");
        } catch (NullPointerException ex) {
            return false;
        }
    }

    public String getDataStoreType() {
        return this.dataStoreType;
    }

    /**
     * Checks if a value is true in the datastore.
     *
     * @param String Db table to check.
     * @param String Db key to check in that table.
     */
    public boolean checkDataStore(String table, String key) {
        try {
            return (dataStore.HasKey(table, "", key) && dataStore.GetString(table, "", key).equals("true"));
        } catch (NullPointerException ex) {
            return false;
        }
    }

    /**
     * Method that returns the basic bot info.
     *
     * @return {String}
     */
    public String getBotInformation() {
        return "\r\nJava Version: " + System.getProperty("java.runtime.version") + "\r\nOS Version: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")\r\nPanel Version: " + RepoVersion.getPanelVersion() + "\r\n" + getBotInfo() + "\r\n\r\n";
    }

    /**
     * Method that gets the PhantomBot properties.
     *
     * @return
     */
    public CaselessProperties getProperties() {
        return this.pbProperties;
    }

    /**
     * Loads everything up.
     */
    private void init() {
        /* Is the web toggle enabled? */
        if (webEnabled) {
            checkPortAvailabity(basePort);
            HTTPWSServer.instance(bindIP, basePort, useHttps, httpsFileName, httpsPassword, botName);
            new HTTPNoAuthHandler().register();
            new HTTPAuthenticatedHandler(webOAuth, oauth.replace("oauth:", "")).register();
            new HTTPPanelAndYTHandler(panelUsername, panelPassword).register();
            this.oauthHandler = (HTTPOAuthHandler) new HTTPOAuthHandler(panelUsername, panelPassword).register();
            if (this.getProperties().getPropertyAsBoolean("useeventsub", false)) {
                EventSub.instance().register();
            }
            panelHandler = (WsPanelHandler) new WsPanelHandler(webOAuthThro, webOAuth).register();
            alertsPollsHandler = (WsAlertsPollsHandler) new WsAlertsPollsHandler(webOAuthThro, webOAuth).register();
            new WsPanelRemoteLoginHandler(panelUsername, panelPassword, webOAuthThro, webOAuth).register();

            /* Is the music toggled on? */
            if (musicEnabled) {
                ytHandler = (WsYTHandler) new WsYTHandler(youtubeOAuthThro, youtubeOAuth).register();
            }
        }

        if (TwitchValidate.instance().isChatValid()) {
            /* Connect to Discord if the data is present. */
            if (!discordToken.isEmpty()) {
                DiscordAPI.instance().connect(discordToken);
            }

            /* Set Streamlabs currency code, if possible */
            if (dataStore.HasKey("donations", "", "currencycode")) {
                TwitchAlertsAPIv1.instance().SetCurrencyCode(dataStore.GetString("donations", "", "currencycode"));
            }

            /* Check to see if all the Twitter info needed is there */
            if (!twitterUsername.isEmpty() && !twitterAccessToken.isEmpty() && !twitterConsumerToken.isEmpty() && !twitterConsumerSecret.isEmpty() && !twitterSecretToken.isEmpty()) {
                /* Set the Twitter tokens */
                TwitterAPI.instance().setUsername(twitterUsername);
                TwitterAPI.instance().setAccessToken(twitterAccessToken);
                TwitterAPI.instance().setSecretToken(twitterSecretToken);
                TwitterAPI.instance().setConsumerKey(twitterConsumerToken);
                TwitterAPI.instance().setConsumerSecret(twitterConsumerSecret);
                /* Check to see if the tokens worked */
                TwitterAPI.instance().authenticate();
            }

            /* print a extra line in the console. */
            print("");

            /* Create configuration for YTPlayer v2.0 for the WS port. */
            String data = "";
            String http = (useHttps ? "https://" : "http://");

            try {
                data += "// Configuration for YTPlayer\r\n";
                data += "// Automatically Generated by PhantomBot at Startup\r\n";
                data += "// Do NOT Modify! Overwritten when PhantomBot is restarted!\r\n";
                data += "var playerPort = " + basePort + ";\r\n";
                data += "var channelName = \"" + channelName + "\";\r\n";
                data += "var auth=\"" + youtubeOAuth + "\";\r\n";
                data += "var http=\"" + http + "\";\r\n";
                data += "function getPlayerPort() { return playerPort; }\r\n";
                data += "function getChannelName() { return channelName; }\r\n";
                data += "function getAuth() { return auth; }\r\n";
                data += "function getProtocol() { return http; }\r\n";

                /* Create a new file if it does not exist */
                if (!new File("./web/ytplayer/").exists()) {
                    new File("./web/ytplayer/").mkdirs();
                }
                if (!new File("./web/ytplayer/js").exists()) {
                    new File("./web/ytplayer/js").mkdirs();
                }

                /* Write the data to that file */
                Files.write(Paths.get("./web/ytplayer/js/playerConfig.js"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }

            /* Create configuration for YTPlayer Playlist v2.0 for the WS port. */
            data = "";
            try {
                data += "//Configuration for YTPlayer\r\n";
                data += "//Automatically Generated by PhantomBot at Startup\r\n";
                data += "//Do NOT Modify! Overwritten when PhantomBot is restarted!\r\n";
                data += "var playerPort = " + basePort + ";\r\n";
                data += "var channelName = \"" + channelName + "\";\r\n";
                data += "var auth=\"" + youtubeOAuthThro + "\";\r\n";
                data += "var http=\"" + http + "\";\r\n";
                data += "function getPlayerPort() { return playerPort; }\r\n";
                data += "function getChannelName() { return channelName; }\r\n";
                data += "function getAuth() { return auth; }\r\n";
                data += "function getProtocol() { return http; }\r\n";

                /* Create a new file if it does not exist */
                if (!new File("./web/playlist/").exists()) {
                    new File("./web/playlist/").mkdirs();
                }
                if (!new File("./web/playlist/js").exists()) {
                    new File("./web/playlist/js").mkdirs();
                }

                /* Write the data to that file */
                Files.write(Paths.get("./web/playlist/js/playerConfig.js"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }

            /* Create configuration for Read-Only Access to WS port. */
            data = "";
            try {
                data += "// Configuration for Control Panel\r\n";
                data += "// Automatically Generated by PhantomBot at Startup\r\n";
                data += "// Do NOT Modify! Overwritten when PhantomBot is restarted!\r\n";
                data += "var panelSettings = {\r\n";
                data += "    panelPort   : " + basePort + ",\r\n";
                data += "    channelName : \"" + channelName + "\",\r\n";
                data += "    auth        : \"" + webOAuthThro + "\",\r\n";
                data += "    http        : \"" + http + "\"\r\n";
                data += "};\r\n\r\n";
                data += "function getPanelPort() { return panelSettings.panelPort; }\r\n";
                data += "function getChannelName() { return panelSettings.channelName; }\r\n";
                data += "function getAuth() { return panelSettings.auth; }\r\n";
                data += "function getProtocol() { return panelSettings.http; }\r\n";

                /* Create a new file if it does not exist */
                if (!new File("./web/common/").exists()) {
                    new File("./web/common/").mkdirs();
                }
                if (!new File("./web/common/js").exists()) {
                    new File("./web/common/js").mkdirs();
                }

                /* Write the data to that file */
                Files.write(Paths.get("./web/common/js/wsConfig.js"), data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }
        }

        /* check if the console is interactive */
        if (interactive) {
            ConsoleInputListener consoleIL = new ConsoleInputListener();
            /* Start the Console Input Listener */
            consoleIL.start();
        }

        /* Register PhantomBot (this) with the event bus. */
        EventBus.instance().register(this);
        /* Register the script manager with the event bus. */
        EventBus.instance().register(ScriptEventManager.instance());
        /* Register the console event handler */
        EventBus.instance().register(ConsoleEventHandler.instance());
        //EventBus.instance().register(tv.phantombot.scripts.core.Moderation.instance());

        if (TwitchValidate.instance().isChatValid()) {
            /* Export all these to the $. api in the scripts. */
            Script.global.defineProperty("inidb", dataStore, 0);
            Script.global.defineProperty("username", UsernameCache.instance(), 0);
            Script.global.defineProperty("twitch", TwitchAPIv5.instance(), 0);
            Script.global.defineProperty("botName", botName.toLowerCase(), 0);
            Script.global.defineProperty("channelName", channelName.toLowerCase(), 0);
            Script.global.defineProperty("ownerName", ownerName.toLowerCase(), 0);
            Script.global.defineProperty("ytplayer", ytHandler, 0);
            Script.global.defineProperty("panelsocketserver", panelHandler, 0);
            Script.global.defineProperty("alertspollssocket", alertsPollsHandler, 0);
            Script.global.defineProperty("random", random, 0);
            Script.global.defineProperty("youtube", YouTubeAPIv3.instance(), 0);
            Script.global.defineProperty("twitter", TwitterAPI.instance(), 0);
            Script.global.defineProperty("twitchCacheReady", PhantomBot.twitchCacheReady, 0);
            Script.global.defineProperty("isNightly", isNightly(), 0);
            Script.global.defineProperty("isPrerelease", isPrerelease(), 0);
            Script.global.defineProperty("version", botVersion(), 0);
            Script.global.defineProperty("changed", newSetup, 0);
            Script.global.defineProperty("discordAPI", DiscordAPI.instance(), 0);
            Script.global.defineProperty("hasDiscordToken", hasDiscordToken(), 0);
            Script.global.defineProperty("customAPI", CustomAPI.instance(), 0);
            Script.global.defineProperty("streamLabsAPI", TwitchAlertsAPIv1.instance(), 0);
            Script.global.defineProperty("moderation", Moderation.instance(), 0);
        }

        /* open a new thread for when the bot is exiting */
        Thread thread = new Thread(() -> {
            onExit();
        }, "tv.phantombot.PhantomBot::onExit");

        /* Get the un time for that new thread we just created */
        Runtime.getRuntime().addShutdownHook(thread);

        if (TwitchValidate.instance().isChatValid()) {
            /* And finally try to load init, that will then load the scripts */
            try {
                ScriptManager.loadScript(new File("./scripts/init.js"));
            } catch (IOException ex) {
                com.gmt2001.Console.err.printStackTrace(ex);
            }
        }

        /* Check for a update with PhantomBot */
        doCheckPhantomBotUpdate();

        /* Perform SQLite datbase backups. */
        if (this.backupDBAuto) {
            doBackupDB();
        }
    }

    /**
     * Used for exiting the bot
     *
     */
    @SuppressWarnings("SleepWhileInLoop")
    public void onExit() {
        print(this.botName + " is shutting down...");

        print("Sending the shutdown event to scripts...");
        EventBus.instance().post(new ShutdownEvent());

        isExiting = true;
        PhantomBot.isInExitState = true;

        print("Stopping all events and message dispatching...");
        ScriptFileWatcher.instance().kill();
        ScriptEventManager.instance().kill();

        /* Gonna need a way to pass this to all channels */
        if (this.getSession() != null) {
            this.getSession().close();
        }

        if (this.wsHostIRC != null) {
            this.wsHostIRC.shutdown();
        }

        if (this.pubSubEdge != null) {
            this.pubSubEdge.shutdown();
        }

        /* Shutdown all caches */
        if (followersCache != null) {
            print("Terminating the Twitch channel follower cache...");
            FollowersCache.killall();
        }

        print("Terminating the Streamlabs cache...");
        DonationsCache.instance().kill();

        if (tipeeeStreamCache != null) {
            print("Terminating the TipeeeStream cache...");
            TipeeeStreamCache.killall();
        }

        if (streamElementCache != null) {
            print("Terminating the StreamElementsCache cache...");
            StreamElementsCache.killall();
        }

        print("Terminating all script modules...");
        HashMap<String, Script> scripts = ScriptManager.getScripts();
        scripts.entrySet().forEach((script) -> {
            script.getValue().kill();
        });

        print("Saving all data...");
        dataStore.SaveAll(true);

        /* Check to see if web is enabled */
        if (webEnabled) {
            print("Shutting down all web socket/http servers...");
            HTTPWSServer.instance().close();
        }

        print("Closing the logs...");
        com.gmt2001.Logger.instance().close();

        try {
            for (int i = 5; i > 0; i--) {
                com.gmt2001.Console.out.print("\rWaiting for everything else to shutdown... " + i + " ");
                Thread.sleep(1000);
            }
        } catch (InterruptedException ex) {
            com.gmt2001.Console.out.print("\r\n");
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        com.gmt2001.Console.out.print("\r\n");
        print("Closing the database...");
        dataStore.dispose();

        this.getProperties().store(false);

        try {
            RollbarProvider.instance().close();
        } catch (Exception ex) {
            com.gmt2001.Console.err.printStackTrace(ex);
        }

        print(this.botName + " is exiting.");
    }

    /**
     * Connected to Twitch.
     *
     */
    @Handler
    public void ircJoinComplete(IrcJoinCompleteEvent event) {
        /* Check if the bot already joined once. */
        this.session.getModerationStatus();

        if (joined) {
            com.gmt2001.Console.debug.println("ircJoinComplete::joined::" + this.channelName);
            return;
        }

        joined = true;

        com.gmt2001.Console.debug.println("ircJoinComplete::" + this.channelName);

        com.gmt2001.Console.debug.println("TwitchValidate.hasAPIScope(channel:moderate)=" + (TwitchValidate.instance().hasAPIScope("channel:moderate") ? "t" : "f"));
        com.gmt2001.Console.debug.println("TwitchValidate.hasAPIScope(channel:read:redemption)=" + (TwitchValidate.instance().hasAPIScope("channel:read:redemptions") ? "t" : "f"));
        com.gmt2001.Console.debug.println("StartPubSub=" + (this.apiOAuth.length() > 0 && (TwitchValidate.instance().hasAPIScope("channel:moderate") || TwitchValidate.instance().hasAPIScope("channel:read:redemptions")) ? "t" : "f"));
        /* Start a pubsub instance here. */
        if (this.apiOAuth.length() > 0 && (TwitchValidate.instance().hasAPIScope("channel:moderate") || TwitchValidate.instance().hasAPIScope("channel:read:redemptions"))) {
            this.pubSubEdge = new TwitchPubSub(this.channelName, TwitchAPIv5.instance().getChannelId(this.channelName), TwitchAPIv5.instance().getChannelId(this.botName), this.apiOAuth);
        }

        /* Load the caches for each channels */
        this.twitchCache = TwitchCache.instance(this.channelName);
        this.twitchTeamCache = TwitchTeamsCache.instance(this.channelName);
        this.emotesCache = EmotesCache.instance(this.channelName);
        this.followersCache = FollowersCache.instance(this.channelName);
        this.viewerListCache = ViewerListCache.instance(this.channelName);

        /* Start the donations cache if the keys are not null and the module is enabled */
        if (this.twitchAlertsKey != null && !this.twitchAlertsKey.isEmpty() && checkModuleEnabled("./handlers/donationHandler.js")) {
            DonationsCache.instance().start();
        }

        /* Start the TipeeeStream cache if the keys are not null and the module is enabled. */
        if (this.tipeeeStreamOAuth != null && !this.tipeeeStreamOAuth.isEmpty() && checkModuleEnabled("./handlers/tipeeeStreamHandler.js")) {
            this.tipeeeStreamCache = TipeeeStreamCache.instance(this.channelName);
        }

        /* Start the StreamElements cache if the keys are not null and the module is enabled. */
        if (this.streamElementsJWT != null && !this.streamElementsJWT.isEmpty() && checkModuleEnabled("./handlers/streamElementsHandler.js")) {
            this.streamElementCache = StreamElementsCache.instance(this.channelName);
        }

        /* Export these to the $. api for the sripts to use */
        Script.global.defineProperty("twitchcache", this.twitchCache, 0);
        Script.global.defineProperty("twitchteamscache", this.twitchTeamCache, 0);
        Script.global.defineProperty("emotes", this.emotesCache, 0);
        Script.global.defineProperty("usernameCache", this.viewerListCache, 0);
    }

    /**
     * Get private messages from Twitch.
     *
     */
    @Handler
    public void ircPrivateMessage(IrcPrivateMessageEvent event) {
        String sender = event.getSender();
        String message = event.getMessage();

        /* Check to see if the sender is jtv */
        if (sender.equalsIgnoreCase("jtv")) {
            /* Splice the mod list so we can get all the mods */
            String searchStr = "The moderators of this channel are: ";
            if (message.startsWith(searchStr)) {
                String[] moderators = message.substring(searchStr.length()).split(", ");

                /* Check to see if the bot is a moderator */
                for (String moderator : moderators) {
                    if (moderator.equalsIgnoreCase(this.botName)) {
                        EventBus.instance().postAsync(new IrcChannelUserModeEvent(this.session, this.session.getBotName(), "O", true));
                        /* Allow the bot to sends message to this session */
                        event.getSession().setAllowSendMessages(true);
                        com.gmt2001.Console.debug.println("Allowing messages to be sent due to .mods response +O");
                    }
                }
            }
        }
    }

    /**
     * user modes from twitch
     *
     */
    @Handler
    public void ircUserMode(IrcChannelUserModeEvent event) {
        /* Check to see if Twitch sent a mode event for the bot name */
        if (event.getUser().equalsIgnoreCase(this.botName) && event.getMode().equalsIgnoreCase("o")) {
            if (!event.getAdd()) {
                event.getSession().getModerationStatus();
            }
            /* Allow the bot to sends message to this session */
            event.getSession().setAllowSendMessages(event.getAdd());
        }
    }

    /**
     * messages from Twitch chat
     *
     */
    @Handler
    public void ircChannelMessage(IrcChannelMessageEvent event) {
        if (this.pubSubEdge != null) {
            this.pubSubEdge.ircChannelMessageEvent(event);
        }
    }

    /**
     * Handle commands
     */
    public void handleCommand(String username, String command) {
        String arguments = "";

        /* Does the command have arguments? */
        if (command.contains(" ")) {
            String commandString = command;
            command = commandString.substring(0, commandString.indexOf(" "));
            arguments = commandString.substring(commandString.indexOf(" ") + 1);
        }
        EventBus.instance().postAsync(new CommandEvent(username, command, arguments));
    }

    /**
     * Handle commands
     */
    public void handleCommandSync(String username, String command) {
        String arguments = "";

        /* Does the command have arguments? */
        if (command.contains(" ")) {
            String commandString = command;
            command = commandString.substring(0, commandString.indexOf(" "));
            arguments = commandString.substring(commandString.indexOf(" ") + 1);
        }
        EventBus.instance().post(new CommandEvent(username, command, arguments));
    }

    /**
     * Load up main
     */
    public static void main(String[] args) throws IOException {
        System.setProperty("io.netty.noUnsafe", "true");

        // Move user files.
        moveUserConfig();

        if (Float.valueOf(System.getProperty("java.specification.version")) < (float) 11) {
            System.out.println("Detected Java " + System.getProperty("java.version") + ". " + "PhantomBot requires Java 11 or later.");
            PhantomBot.exitError();
        }

        /* Print the user dir */
        com.gmt2001.Console.out.println("The working directory is: " + GetExecutionPath());

        com.gmt2001.Console.out.println("Detected Java " + System.getProperty("java.version") + " running on "
                + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");

        /* If prompted, now that the version has been reported, exit. */
        if (args.length > 0) {
            if (args[0].equals("--version") || args[0].equals("-v")) {
                com.gmt2001.Console.out.println("PhantomBot Version: " + RepoVersion.getPhantomBotVersion() + " (" + RepoVersion.getRepoVersion() + ")");
                PhantomBot.exitOK();
            }
        }

        CaselessProperties startProperties = ConfigurationManager.getConfiguration();

        setStaticFields(startProperties);

        /* Start PhantomBot */
        PhantomBot.instance = new PhantomBot(startProperties);
    }

    private static void setStaticFields(CaselessProperties startProperties) {
        /* Check to enable debug mode */
        PhantomBot.setDebugging(ConfigurationManager.getBoolean(startProperties, ConfigurationManager.PROP_DEBUGON, false));
        /* Check to enable debug to File */
        PhantomBot.setDebuggingLogOnly(ConfigurationManager.getBoolean(startProperties, ConfigurationManager.PROP_DEBUGLOG, false));
        /* Check to enable Script Reloading */
        PhantomBot.setReloadScripts(ConfigurationManager.getBoolean(startProperties, ConfigurationManager.PROP_RELOADSCRIPTS, false));
        /* Check to silence the loading of scripts at startup. */
        PhantomBot.setSilentScriptsLoad(ConfigurationManager.getBoolean(startProperties, ConfigurationManager.PROP_SILENTSCRIPTSLOAD, false));
        /* Check to enable Rhino Debugger */
        PhantomBot.setEnableRhinoDebugger(ConfigurationManager.getBoolean(startProperties, ConfigurationManager.PROP_RHINODEBUGGER, false));

        PhantomBot.setEnableRhinoES6(startProperties.getPropertyAsBoolean("rhino_es6", false));
    }

    private static void setEnableRhinoDebugger(boolean enableRhinoDebugger) {
        if (enableRhinoDebugger) {
            com.gmt2001.Console.out.println("Rhino Debugger will be launched if system supports it.");
        }
        PhantomBot.enableRhinoDebugger = enableRhinoDebugger;
    }

    private static void setEnableRhinoES6(boolean enableRhinoES6) {
        if (enableRhinoES6) {
            com.gmt2001.Console.out.println("Rhino ECMAScript6 support enabled.");
        }
        PhantomBot.enableRhinoES6 = enableRhinoES6;
    }

    private static void setReloadScripts(boolean reloadScripts) {
        if (reloadScripts) {
            com.gmt2001.Console.out.println("Enabling Script Reloading");
        }
        PhantomBot.reloadScripts = reloadScripts;

    }

    private static void setSilentScriptsLoad(boolean silentScriptsLoad) {
        if (silentScriptsLoad) {
            com.gmt2001.Console.out.println("Enabling Silent Script Load");
        }
        PhantomBot.silentScriptsLoad = silentScriptsLoad;

    }

    /**
     * gen a random string
     */
    public static String generateRandomString(int length) {
        String randomAllowed = "01234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        char[] randomChars = randomAllowed.toCharArray();
        char[] randomBuffer;

        randomBuffer = new char[length];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < randomBuffer.length; i++) {
            randomBuffer[i] = randomChars[random.nextInt(randomChars.length)];
        }
        return new String(randomBuffer);
    }

    public static String getOsSuffix() {
        String os = "";
        String osname = System.getProperty("os.name").toLowerCase();

        if (osname.contains("win")) {
            os = "-win";
        } else if (osname.contains("mac")) {
            os = "-mac";
        } else if (osname.contains("bsd")) {
            os = "-arm-bsd-other";
        } else if (osname.contains("nix") || osname.contains("nux") || osname.contains("aix")) {
            if (System.getProperty("os.arch").toLowerCase().contains("arm")) {
                os = "-arm-bsd-other";
            } else {
                os = "-lin";
            }
        }

        return os;
    }

    /**
     * doCheckPhantomBotUpdate
     */
    private void doCheckPhantomBotUpdate() {
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            if (!RepoVersion.getBuildType().startsWith("edge") && !RepoVersion.getBuildType().startsWith("custom")) {
                try {
                    Thread.currentThread().setName("tv.phantombot.PhantomBot::doCheckPhantomBotUpdate");

                    if (RepoVersion.getNightlyBuild()) {
                        String latestNightly = HttpRequest.getData(HttpRequest.RequestType.GET, "https://raw.githubusercontent.com/PhantomBot/nightly-build/master/last_repo_version", null, null).content.trim();
                        if (latestNightly.equalsIgnoreCase(RepoVersion.getRepoVersion().trim())) {
                            dataStore.del("settings", "newrelease_info");
                        } else {
                            try {
                                Thread.sleep(6000);
                                print("");
                                print("New PhantomBot Nightly Build Detected: " + latestNightly);
                                print("Download Link: https://github.com/PhantomBot/nightly-build/raw/master/PhantomBot-nightly" + PhantomBot.getOsSuffix() + ".zip");
                                print("A reminder will be provided in 24 hours!");
                                print("");
                            } catch (InterruptedException ex) {
                                com.gmt2001.Console.err.printStackTrace(ex);
                            }

                            if (webEnabled) {
                                dataStore.set("settings", "newrelease_info", "nightly-" + latestNightly + "|https://github.com/PhantomBot/nightly-build/raw/master/PhantomBot-nightly" + PhantomBot.getOsSuffix() + ".zip");
                            }
                        }
                    } else {
                        String[] newVersionInfo = GitHubAPIv3.instance().CheckNewRelease();
                        if (newVersionInfo != null) {
                            try {
                                Thread.sleep(6000);
                                print("");
                                print("New PhantomBot Release Detected: " + newVersionInfo[0]);
                                print("Release Changelog: https://github.com/PhantomBot/PhantomBot/releases/" + newVersionInfo[0]);
                                print("Download Link: " + newVersionInfo[1]);
                                print("A reminder will be provided in 24 hours!");
                                print("");
                            } catch (InterruptedException ex) {
                                com.gmt2001.Console.err.printStackTrace(ex);
                            }

                            if (webEnabled) {
                                dataStore.set("settings", "newrelease_info", newVersionInfo[0] + "|" + newVersionInfo[1]);
                            }
                        } else {
                            dataStore.del("settings", "newrelease_info");
                        }
                    }
                } catch (JSONException ex) {
                    com.gmt2001.Console.err.logStackTrace(ex);
                }
            }

            try {
                Thread.sleep(30000);
                GamesListUpdater.update();
            } catch (InterruptedException | JSONException ex) {
                com.gmt2001.Console.err.logStackTrace(ex);
            }
        }, 0, 24, TimeUnit.HOURS);
    }

    /**
     * Set the twitch cache
     */
    public void setTwitchCacheReady(String twitchCacheReady) {
        PhantomBot.twitchCacheReady = twitchCacheReady;
        Script.global.defineProperty("twitchCacheReady", PhantomBot.twitchCacheReady, 0);
    }

    /**
     * Backup the database, keeping so many days.
     */
    private void doBackupDB() {
        if (!this.dataStore.canBackup()) {
            return;
        }

        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(() -> {
            Thread.currentThread().setName("tv.phantombot.PhantomBot::doBackupDB");

            SimpleDateFormat datefmt = new SimpleDateFormat("ddMMyyyy.hhmmss");
            datefmt.setTimeZone(TimeZone.getTimeZone(timeZone));
            String timestamp = datefmt.format(new Date());

            dataStore.backupDB("phantombot.auto.backup." + timestamp + ".db");

            try {
                Iterator<File> dirIterator = FileUtils.iterateFiles(new File("./dbbackup"), new WildcardFileFilter("phantombot.auto.*"), null);
                while (dirIterator.hasNext()) {
                    File backupFile = dirIterator.next();
                    if (FileUtils.isFileOlder(backupFile, (System.currentTimeMillis() - (long) (backupDBKeepDays * 864e5)))) {
                        FileUtils.deleteQuietly(backupFile);
                    }
                }
            } catch (Exception ex) {
                com.gmt2001.Console.err.println("Failed to clean up database backup directory: " + ex.getMessage());
            }
        }, 0, backupDBHourFrequency, TimeUnit.HOURS);
    }

    /**
     * Method that moves the db and botlogin into a new folder (config)
     */
    private static void moveUserConfig() {
        // Check if the config folder exists.
        if (!new File("./config/").isDirectory()) {
            new File("./config/").mkdir();
        }

        // Move the db and login file. If one of these doesn't exist it means this is a new bot.
        if (!new File("phantombot.db").exists() || !new File("botlogin.txt").exists()) {
            return;
        }

        com.gmt2001.Console.out.println("Moving the phantombot.db and botlogin.txt files into ./config");

        try {
            Files.move(Paths.get("botlogin.txt"), Paths.get("./config/botlogin.txt"));
            Files.move(Paths.get("phantombot.db"), Paths.get("./config/phantombot.db"));

            try {
                new File("phantombot.db").delete();
                new File("botlogin.txt").delete();
            } catch (Exception ex) {
                com.gmt2001.Console.err.println("Failed to delete files [phantombot.db] [botlogin.txt] [" + ex.getClass().getSimpleName() + "]: " + ex.getMessage());
            }
        } catch (IOException ex) {
            com.gmt2001.Console.err.println("Failed to move files [phantombot.db] [botlogin.txt] [" + ex.getClass().getSimpleName() + "]: " + ex.getMessage());
        }

        // Move audio hooks and alerts. These two files should always exists.
        if (!new File("./web/panel/js/ion-sound/sounds").exists() || !new File("./web/alerts/data").exists()) {
            return;
        }

        com.gmt2001.Console.out.println("Moving alerts and audio hooks into ./config");

        try {
            Files.move(Paths.get("./web/panel/js/ion-sound/sounds"), Paths.get("./config/audio-hooks"));
            Files.move(Paths.get("./web/alerts/data"), Paths.get("./config/gif-alerts"));

            try {
                FileUtils.deleteDirectory(new File("./web/panel/js/ion-sound/sounds"));
                FileUtils.deleteDirectory(new File("./web/alerts/data"));
            } catch (IOException ex) {
                com.gmt2001.Console.err.println("Failed to delete old audio hooks and alerts [" + ex.getClass().getSimpleName() + "]: " + ex.getMessage());
            }
        } catch (IOException ex) {
            com.gmt2001.Console.err.println("Failed to move audio hooks and alerts [" + ex.getClass().getSimpleName() + "]: " + ex.getMessage());
        }
    }

    /**
     * Method to export a Java list to a csv file.
     *
     * @param {String[]} headers
     * @param {List} values
     * @param {String} fileName
     */
    public void toCSV(String[] headers, List<String[]> values, String fileName) {
        StringBuilder builder = new StringBuilder();
        FileOutputStream stream = null;

        // Append the headers.
        builder.append(String.join(",", headers)).append("\n");

        // Append all values.
        values.forEach((value) -> {
            builder.append(String.join(",", value)).append("\n");
        });

        // Write the data to a file.
        try {
            // Create a new stream.
            stream = new FileOutputStream(new File(fileName));

            // Write the content.
            stream.write(builder.toString().getBytes(Charset.forName("UTF-8")));
            stream.flush();
        } catch (IOException ex) {
            com.gmt2001.Console.err.println("Failed writing data to file [IOException]: " + ex.getMessage());
        } catch (SecurityException ex) {
            com.gmt2001.Console.err.println("Failed writing data to file [SecurityException]: " + ex.getMessage());
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    com.gmt2001.Console.err.printStackTrace(ex);
                }
            }
        }
    }

    /**
     * End PhantomBot with an error state
     */
    public static void exitError() {
        System.exit(EXIT_STATUS_ERROR);
    }

    /**
     * End PhantomBot with an OK state
     */
    public static void exitOK() {
        System.exit(EXIT_STATUS_OK);
    }

    public static boolean getReloadScripts() {
        return reloadScripts;
    }

    public static boolean getSilentScriptsLoad() {
        return silentScriptsLoad;
    }

    public static boolean getEnableDebugging() {
        return enableDebugging;
    }

    public static boolean getEnableDebuggingLogOnly() {
        return enableDebuggingLogOnly;
    }

    public static boolean getEnableRhinoDebugger() {
        return enableRhinoDebugger;
    }

    public static boolean getEnableRhinoES6() {
        return enableRhinoES6;
    }

    public static String getTimeZone() {
        return timeZone;
    }

    public static boolean getTwitchTcpNodelay() {
        return twitchTcpNodelay;
    }

    public static boolean isInExitState() {
        return isInExitState;
    }

    public void setPubSub(TwitchPubSub pubSub) {
        this.pubSubEdge = pubSub;
    }

    public void setHostIRC(TwitchWSHostIRC hostIrc) {
        this.wsHostIRC = hostIrc;
    }

    public void setSession(TwitchSession session) {
        this.session = session;
    }
}
