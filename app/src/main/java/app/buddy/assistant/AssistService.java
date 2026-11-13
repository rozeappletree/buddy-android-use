package app.buddy.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;
import android.speech.RecognitionService;

/**
 * Lets the user pick Buddy as the phone's digital assistant (Settings → Apps → Default apps → Digital assistant app).
 * The assistant gesture (hold power or home, swipe from a corner) then starts the same listening as the bubble's Talk.
 */
public class AssistService extends VoiceInteractionService {
    /** Opens Android's list of assistant apps. */
    static Intent settingsIntent() {
        return new Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** One assistant session: starts listening and goes away; the waves and words show on the bubble. */
    public static class SessionService extends VoiceInteractionSessionService {
        @Override
        public VoiceInteractionSession onNewSession(Bundle args) {
            return new Session(this);
        }
    }

    static final class Session extends VoiceInteractionSession {
        Session(android.content.Context c) {
            super(c);
        }

        @Override
        public void onShow(Bundle args, int flags) {
            super.onShow(args, flags);
            BuddyService svc = BuddyService.get();
            if (svc == null || svc.bubble() == null) { // accessibility is off: open Buddy so the user can turn it on
                startAssistantActivity(new Intent(getContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                svc.bubble().assist(); // what AssistActivity does: listen, or open the chat if the bubble is hidden
            }
            hide();
        }
    }

    /**
     * Android needs a recognition service named for an assistant. Buddy has none of its own (it uses the phone's,
     * Claude's or ChatGPT's), so this one only reports an error; Voice never picks it (see Voice.recognizer).
     */
    public static class NoRecognizer extends RecognitionService {
        @Override
        protected void onStartListening(Intent intent, Callback callback) {
            try {
                callback.error(android.speech.SpeechRecognizer.ERROR_CLIENT);
            } catch (android.os.RemoteException ignored) {
            }
        }

        @Override
        protected void onCancel(Callback callback) {
        }

        @Override
        protected void onStopListening(Callback callback) {
        }
    }
}
