package fr.lordfinn.crazyphone.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;

import fr.lordfinn.crazyphone.Crazyphone;

/**
 * SVC discovers and loads this class itself via classpath scanning for {@link ForgeVoicechatPlugin} - it is
 * never referenced from anywhere else in this mod, so it's safe for it to import SVC types directly even
 * though SVC may not be installed (see {@link VoicechatIntegration}'s javadoc): if SVC is absent, SVC's own
 * plugin discovery never runs and this class is simply never loaded.
 */
@ForgeVoicechatPlugin
public class CrazyPhoneVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return Crazyphone.MODID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi serverApi) {
            SvcCallBridge.setServerApi(serverApi);
        } else if (api instanceof VoicechatClientApi clientApi) {
            SvcCallBridge.setClientApi(clientApi);
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // Voice-message recording (VoiceMessageRecorder) captures directly from the OS microphone via
        // javax.sound.sampled rather than hooking an SVC transmission event - see that class's javadoc for
        // why - so there is nothing to register here for it.
    }
}
