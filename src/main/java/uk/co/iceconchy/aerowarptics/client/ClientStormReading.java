package uk.co.iceconchy.aerowarptics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import uk.co.iceconchy.aerowarptics.weather.StormForecast;

/**
 * What a Stormglass reads on the server this client is connected to, as the storm packet last said.
 *
 * <p>Clear until a server says otherwise: a glass read before joining a world forecasts nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientStormReading {

    private static int reading;

    private ClientStormReading() {
    }

    static void set(int ordinal) {
        reading = ordinal;
    }

    public static StormForecast.Reading get() {
        return StormForecast.Reading.byOrdinal(reading);
    }
}
