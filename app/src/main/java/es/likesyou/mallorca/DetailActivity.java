package es.likesyou.mallorca;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

public class DetailActivity extends Activity {
    private JSONObject place;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            place = new JSONObject(getIntent().getStringExtra("place_json"));
        } catch (Exception e) {
            finish();
            return;
        }
        buildDetail();
    }

    private void buildDetail() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F6F7);

        TextView toolbar = new TextView(this);
        toolbar.setText("‹   Mallorca Likes You");
        toolbar.setTextSize(18);
        toolbar.setTextColor(0xFFFFFFFF);
        toolbar.setBackgroundColor(0xFF176B67);
        toolbar.setPadding(dp(16), dp(16), dp(16), dp(16));
        toolbar.setOnClickListener(v -> finish());
        root.addView(toolbar);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView title = new TextView(this);
        title.setText(place.optString("lugar", place.optString("nombre", "Lugar")));
        title.setTextSize(26);
        title.setTypeface(null, 1);
        title.setTextColor(0xFF183A39);
        content.addView(title);

        String meta = place.optString("categoria", "");
        String municipality = place.optString("municipio", "");
        if (!municipality.isEmpty()) meta += " · " + municipality;
        double distance = place.optDouble("distancia_km", -1);
        if (distance >= 0) meta += " · " + String.format(java.util.Locale.getDefault(), "%.2f km", distance);
        addText(meta, 14, 0xFF52706E, false, dp(5));

        addText(place.optString("desc_corta", place.optString("descripcion", "")), 16, 0xFF303838, false, dp(12));

        String other = place.optString("otros", "");
        if (!other.isEmpty()) addText("Características: " + other, 13, 0xFF596563, false, dp(12));

        TextView mediaHeading = new TextView(this);
        mediaHeading.setText("Fotografías y vídeos");
        mediaHeading.setTextSize(19);
        mediaHeading.setTypeface(null, 1);
        mediaHeading.setTextColor(0xFF183A39);
        mediaHeading.setPadding(0, dp(8), 0, dp(10));
        content.addView(mediaHeading);

        int mediaCount = addMedia(place.optJSONArray("fotos"));
        mediaCount += addMedia(place.optJSONArray("videos"));
        if (mediaCount == 0) addText("Este lugar todavía no tiene fotografías ni vídeos.", 14, 0xFF687574, false, dp(8));

        TextView mapButton = new TextView(this);
        mapButton.setText("Cómo llegar con Google Maps  ↗");
        mapButton.setGravity(Gravity.CENTER);
        mapButton.setTextSize(16);
        mapButton.setTypeface(null, 1);
        mapButton.setTextColor(0xFFFFFFFF);
        mapButton.setBackgroundColor(0xFF176B67);
        mapButton.setPadding(dp(12), dp(16), dp(12), dp(16));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.setMargins(0, dp(18), 0, dp(12));
        content.addView(mapButton, buttonParams);
        mapButton.setOnClickListener(v -> openMaps());
        setContentView(root);
    }

    private int addMedia(JSONArray mediaArray) {
        if (mediaArray == null) return 0;
        int count = 0;
        for (int i = 0; i < mediaArray.length(); i++) {
            JSONObject media = mediaArray.optJSONObject(i);
            if (media == null) continue;
            String url = MainActivity.mediaUrl(media);
            if (url.isEmpty()) continue;
            count++;

            if (MainActivity.isVideo(media, url)) {
                VideoView video = new VideoView(this);
                LinearLayout.LayoutParams videoParams = new LinearLayout.LayoutParams(-1, dp(220));
                videoParams.setMargins(0, dp(8), 0, dp(4));
                content.addView(video, videoParams);
                video.setVideoURI(Uri.parse(url));
                MediaController controls = new MediaController(this);
                controls.setAnchorView(video);
                video.setMediaController(controls);
            } else {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(220));
                imageParams.setMargins(0, dp(8), 0, dp(4));
                content.addView(image, imageParams);
                loadImage(url, image);
            }

            String author = MainActivity.mediaAuthor(media);
            addText("Autor: " + author, 12, 0xFF687574, false, dp(8));
        }
        return count;
    }

    private void addText(String value, int size, int color, boolean bold, int bottomPadding) {
        if (value == null || value.trim().isEmpty()) return;
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, 1);
        text.setPadding(0, dp(2), 0, bottomPadding);
        content.addView(text);
    }

    private void loadImage(String imageUrl, ImageView target) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.connect();
                Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                connection.disconnect();
                if (bitmap != null) runOnUiThread(() -> target.setImageBitmap(bitmap));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void openMaps() {
        double lat = place.optDouble("lat", Double.NaN);
        double lng = place.optDouble("lng", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng)) {
            android.widget.Toast.makeText(this, "Este lugar no tiene coordenadas disponibles.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String label = Uri.encode(place.optString("lugar", "Mallorca"));
        Uri navigation = Uri.parse("google.navigation:q=" + lat + "," + lng + "&mode=d");
        Intent intent = new Intent(Intent.ACTION_VIEW, navigation);
        intent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Uri geo = Uri.parse("geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + label + ")");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, geo));
            } catch (Exception ignored) {
                android.widget.Toast.makeText(this, "No hay ninguna aplicación de mapas disponible.", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
