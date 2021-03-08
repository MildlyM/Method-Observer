package com.methodtournamentclient;

import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.*;
import org.apache.commons.lang3.time.StopWatch;

import javax.inject.Inject;
import java.io.IOException;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
        name = "Method Tournament",
        enabledByDefault = false,
        description = "Sends information from your client to a database, to be displayed on stream"
)
public class TournamentPlugin extends Plugin
{
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private TournamentConfig config;
    @Inject
    private ItemManager itemManager;

    private OkHttpClient okClient = new OkHttpClient();
    private Player opponent;
    ItemContainer itemContainer = null;
    List<Integer> itemList = new ArrayList<>();
    StopWatch watch = new StopWatch();

    private String inventSend;
    private int badResponses = 0;
    private int onPrayHits = 0;
    private int offPrayHits = 0;
    private int damageReceived = 0;
    private int damageDealt = 0;

    private final List<Integer> MELEE_ATTACKS = Arrays.asList(376, 381, 386, 390, 393, 393, 395, 400,
            401, 406, 407, 414, 419, 422, 423, 428, 429, 440, 1058, 1060, 1062, 1378, 1658, 1665, 1667,
            2066, 2067, 2078, 2661, 3297, 3298, 3852, 5865, 7004, 7045, 7054, 7514, 7515, 7516, 7638,
            7639, 7640, 7641, 7642, 7643, 7644, 7645, 8056, 8145);
    private final List<Integer> RANGE_ATTACKS = Arrays.asList(426,929,1074,4230,5061,6600,7218,
            7521,7552,7555,7617,8194,8195,8292);
    private final List<Integer> MAGE_ATTACKS = Arrays.asList(710,711,1161,1162,1167,7855,1978,
            1979,8532);

    private final WorldArea DUEL_ARENA = new WorldArea(3333, 3244, 25, 15, 0);

    @Provides
    TournamentConfig provideConfig(final ConfigManager configManager)
    {
        return configManager.getConfig(TournamentConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        itemContainer = null;
        itemList.clear();
        badResponses = 0;
        opponent = null;
        inventSend = "";
        onPrayHits = 0;
        offPrayHits = 0;
        damageReceived = 0;
        damageDealt = 0;

        if (watch.isStarted())
        {
            watch.stop();
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        itemContainer = null;
        itemList.clear();
        badResponses = 0;
        opponent = null;
        inventSend = "";
        onPrayHits = 0;
        offPrayHits = 0;
        damageReceived = 0;
        damageDealt = 0;

        if (watch.isStarted())
        {
            watch.stop();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("tournament"))
        {
            return;
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event)
    {
        if (event.getCommand().equals("test"))
        {
            test();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() == GameState.LOGIN_SCREEN || badResponses > 10)
        {
            return;
        }

        if (client.getLocalPlayer() == null || opponent == null)
        {
            return;
        }

        if (!client.getLocalPlayer().getWorldArea().intersectsWith(DUEL_ARENA))
        {
            resetValues();
            return;
        }

        if (config.enable())
        {
            sendPostRequest(inventSend, client.getLocalPlayer().getName(), false);
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!config.enable() || client.getGameState() == GameState.LOGIN_SCREEN || badResponses > 10)
        {
            return;
        }

        final ItemContainer container = event.getItemContainer();

        if (container == client.getItemContainer(InventoryID.INVENTORY))
        {
            captureInvent();

            if (isValidUrl(config.endpoint()))
            {
                if (config.enable() && !client.getLocalPlayer().getWorldArea().intersectsWith(DUEL_ARENA))
                {
                    sendPostRequest(inventSend, client.getLocalPlayer().getName(), false);
                }
            }
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event)
    {
        if (client.getLocalPlayer() == null || opponent == null)
        {
            return;
        }

        Player local = client.getLocalPlayer();

        if (event.getActor().equals(local)) {
            if (MELEE_ATTACKS.contains(event.getActor().getAnimation())) {
                if (opponent.getOverheadIcon() != null && opponent.getOverheadIcon().equals(HeadIcon.MELEE)) {
                    onPrayHits++;
                } else {
                    offPrayHits++;
                }
            }

            if (RANGE_ATTACKS.contains(event.getActor().getAnimation())) {
                if (opponent.getOverheadIcon() != null && opponent.getOverheadIcon().equals(HeadIcon.RANGED)) {
                    onPrayHits++;
                } else {
                    offPrayHits++;
                }
            }

            if (MAGE_ATTACKS.contains(event.getActor().getAnimation())) {
                if (opponent.getOverheadIcon() != null && opponent.getOverheadIcon().equals(HeadIcon.MAGIC)) {
                    onPrayHits++;
                } else {
                    offPrayHits++;
                }
            }
        }
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (client.getLocalPlayer() == null || opponent == null)
        {
            return;
        }

        if (client.getLocalPlayer().getWorldArea().intersectsWith(DUEL_ARENA))
        {
            if (event.getActor().equals(client.getLocalPlayer()))
            {
                damageReceived += event.getHitsplat().getAmount();
            }

            if (event.getActor().equals(opponent))
            {
                damageDealt += event.getHitsplat().getAmount();
            }
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (client.getLocalPlayer() == null)
        {
            return;
        }

        if (client.getLocalPlayer().getWorldArea().intersectsWith(DUEL_ARENA))
        {
            if (event.getSource() == client.getLocalPlayer())
            {
                Actor target = event.getTarget();

                if (target == opponent)
                {
                    return;
                }

                if (opponent == null)
                {
                    if (target instanceof  Player)
                    {
                        resetValues();

                        opponent = (Player) target;

                        if (config.debug())
                        {
                            clientThread.invokeLater(() ->
                            {
                                client.addChatMessage(ChatMessageType.ENGINE, "", "Opponent set: " + opponent.getName(), "");
                            });
                        }
                    }
                }
                return;
            }

            if (event.getTarget() instanceof Player)
            {
                if (event.getSource() != client.getLocalPlayer() &&  event.getTarget().equals(client.getLocalPlayer()) && event.getSource().getWorldArea().intersectsWith(DUEL_ARENA))
                {
                    Actor target = event.getTarget();
                    Actor source = event.getSource();

                    if (target.equals(client.getLocalPlayer()))
                    {
                        if (source == opponent)
                        {
                            return;
                        }

                        if (opponent == null)
                        {
                            if (source instanceof  Player)
                            {
                                resetValues();

                                opponent = (Player) source;

                                if (config.debug())
                                {
                                    clientThread.invokeLater(() ->
                                    {
                                        client.addChatMessage(ChatMessageType.ENGINE, "", "Opponent set: " + opponent.getName(), "");
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    public void resetValues()
    {
        if (config.debug() && opponent != null)
        {
            clientThread.invokeLater(() ->
            {
                client.addChatMessage(ChatMessageType.ENGINE, "", "Opponent reset", "");
            });
        }
        opponent = null;
        badResponses = 0;
        onPrayHits = 0;
        offPrayHits = 0;
        damageReceived = 0;
        damageDealt = 0;
        itemContainer = null;
        itemList.clear();
    }

    private int getMaxHp()
    {
        if (client.getLocalPlayer() == null)
        {
            return 99;
        }

        return client.getRealSkillLevel(Skill.HITPOINTS);
    }

    private int getCurrentHp()
    {
        if (client.getLocalPlayer() == null)
        {
            return 99;
        }

        return client.getBoostedSkillLevel(Skill.HITPOINTS);
    }

    private int getTotalHits()
    {
        return onPrayHits + offPrayHits;
    }

    private String getPrayPercent()
    {
        double result = (double) offPrayHits / getTotalHits() * 100;
        int toPrint = (int) result;
        return toPrint + "%";
    }

    private String getPrayers()
    {
        String output = offPrayHits + " / " + getTotalHits() + "(" + getPrayPercent() + ")";
        return output;
    }

    private String getDamage()
    {
        String posneg;
        if (damageDealt > damageReceived)
        {
            posneg = "+";
        }
        else {
            posneg = "";
        }
        String output = damageDealt + "(" + posneg + (damageDealt - damageReceived) + ")";
        return output;
    }

    private void captureInvent()
    {
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            if (config.debug())
            {
                log.info("Local player null");
            }
            return;
        }

        itemContainer = client.getItemContainer(InventoryID.INVENTORY);

        if (itemContainer == null)
        {
            if (config.debug())
            {
                log.info("Inventory null");
            }
            return;
        }

        Item[] items = itemContainer.getItems();
        itemList.clear();

        for (Item item : items)
        {
            itemList.add(item.getId());
        }

        inventSend = (itemList.toString());
    }

    private void test()
    {
        if (client.getLocalPlayer() == null)
        {
            clientThread.invokeLater(() ->
            {
                client.addChatMessage(ChatMessageType.ENGINE, "", "Test: Local Player null, cancelling test", "");
            });
        }

        badResponses = 0;

        if (config.endpoint().equals(""))
        {
            clientThread.invokeLater(() ->
            {
                client.addChatMessage(ChatMessageType.ENGINE, "", "Test: Endpoint empty, please enter the endpoint provided to you, in the config", "");
            });
            return;
        }

        if (config.password().equals(""))
        {
            clientThread.invokeLater(() ->
            {
                client.addChatMessage(ChatMessageType.ENGINE, "", "Test: Password empty, please enter the tournament password provided to you, in the config", "");
            });
            return;
        }

        if (!isValidUrl(config.endpoint()))
        {
            clientThread.invokeLater(() ->
            {
                client.addChatMessage(ChatMessageType.ENGINE, "", "Test: Endpoint is not a valid URL", "");
            });
            return;
        }

        if (client.getLocalPlayer().getName() != null)
        {
            Random r = new Random();
            String inventSend = "[22840, 22840, 22840, 22840, 22840, 22840, 22840, 22840, 22840, 22840, " + r.nextInt(25519) + "]";

            sendPostRequest(inventSend, client.getLocalPlayer().getName(), true);
        }
    }

    private void sendPostRequest(String inventSend, String user, Boolean test)
    {
        HttpUrl url = HttpUrl.parse(config.endpoint());

        JsonObject jsonObject = new JsonObject();
        try {
            jsonObject.addProperty("user", user);
            jsonObject.addProperty("pass", config.password());
            jsonObject.addProperty("invent", inventSend);
            jsonObject.addProperty("currenthp", getCurrentHp());
            jsonObject.addProperty("maxhp", getMaxHp());
            jsonObject.addProperty("prayers", getPrayers());
            jsonObject.addProperty("damage", getDamage());
        } catch (JsonIOException e){
            e.printStackTrace();
        }

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        RequestBody body = RequestBody.create(JSON, jsonObject.toString());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        watch.reset();
        watch.start();
        okClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failure");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (test)
                {
                    handleTestResponse(response);
                }
                if (!test)
                {
                    handleResponse(response);
                }
                response.close();
            }
        });
    }

    private void handleResponse(Response response) throws IOException
    {
        if (watch.isStarted())
        {
            watch.stop();
        }

        String content = response.body().string();

        long time = watch.getTime(TimeUnit.MILLISECONDS);

        if (content == null)
        {
            if (config.debug())
            {
                log.info(time +"ms -Null Http response");
                badResponses++;
            }
            return;
        }

        if (content.isEmpty())
        {
            if (config.debug())
            {
                log.info(time +"ms -Empty body Http response");
                badResponses++;
            }
            return;
        }

        if (content.contains("Internal server error"))
        {
            if (config.debug())
            {
                log.info(time + "ms - Bad response, internal server error");
                badResponses ++;
            }
            return;
        }

        if (content.equals("UNKNOWN_PLAYER"))
        {
            if (config.debug())
            {
                log.info(time +"ms -Bad http request: Unknown player");
                badResponses++;
            }
            return;
        }

        if (content.equals("NO_CHANGE"))
        {
            if (config.debug())
            {
                log.info(time +"ms -Http request: No Database change, variables didn't change or player not in database");
            }
            return;
        }

        if (content.equals("UPDATE_SUCCESS"))
        {
            if (config.debug())
            {
                log.info(time +"ms -SUCCESS: Database updated");
                return;
            }
        }

        if (config.debug())
        {
            log.info(time +"ms -Exception, seek help");
        }
    }

    private void handleTestResponse(Response response) throws IOException
    {
        String content = response.body().string();

        log.info(content);

        String output = "";

        if (watch.isStarted())
        {
            watch.stop();
        }
        long time = watch.getTime(TimeUnit.MILLISECONDS);

        if (content == null || content.isEmpty())
        {
            output += (time +"ms -FAILURE: Http response body empty");
        }

        if (content != null)
        {
            if (content.contains("Internal server error"))
            {
                output += (time + "ms - FAILURE: Internal server error");
            }

            if (content.equals("NO_CHANGE"))
            {
                output += (time +"ms -FAILURE: No database change, ");
            }

            if (content.equals("UNAUTHORISED"))
            {
                output += (time +"ms -FAILURE: Invalid Password");
            }

            if (content.equals("UPDATE_SUCCESS"))
            {
                output += (time +"ms -SUCCESS: Connection completed with good response from server");
            }
        }

        String finalOutput = output;
        clientThread.invokeLater(() ->
        {
            client.addChatMessage(ChatMessageType.ENGINE, "", "Test: " + finalOutput, "");
        });
    }

    public static boolean isValidUrl(String url)
    {
        try {
            new URL(url).toURI();
            return true;
        }

        catch (Exception e) {
            return false;
        }
    }
}