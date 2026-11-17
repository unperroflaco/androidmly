package es.likesyou.mallorca;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int LOCATION_PERMISSION_REQUEST = 71;
    private static final double DEFAULT_LAT = 39.6953;
    private static final double DEFAULT_LNG = 3.0176;
    private static final String API_BASE = "https://mallorca.likesyou.org/backend/api/buscar.php";

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private LinearLayout resultsContainer;
    private TextView statusText;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();
        startLocationFlow();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F6F7);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(12));
        header.setBackgroundColor(0xFF176B67);

        TextView title = new TextView(this);
        title.setText("Mallorca Likes You");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setTypeface(null, 1);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Descubre lugares cerca de ti");
        subtitle.setTextColor(0xFFE0F2F1);
        subtitle.setTextSize(14);
        header.addView(subtitle);

        statusText = new TextView(this);
        statusText.setText("Obteniendo ubicación…");
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(10), 0, 0);
        header.addView(statusText);
        root.addView(header);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        actionRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        Button refresh = new Button(this);
        refresh.setText("Actualizar lugares");
        refresh.setOnClickListener(v -> startLocationFlow());
        actionRow.addView(refresh);
        root.addView(actionRow);

        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        ScrollView scroll = new ScrollView(this);
        resultsContainer = new LinearLayout(this);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        resultsContainer.setPadding(dp(12), dp(4), dp(12), dp(18));
        scroll.addView(resultsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void startLocationFlow() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST);
            return;
        }

        statusText.setText("Localizando el teléfono…");
        CancellationTokenSource cancellation = new CancellationTokenSource();
        try {
            LocationServices.getFusedLocationProviderClient(this)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.getToken())
                    .addOnSuccessListener(location -> useLocationOrDefault(location))
                    .addOnFailureListener(error -> loadPlaces(DEFAULT_LAT, DEFAULT_LNG,
                            "No se pudo obtener el GPS. Usando ubicación de referencia."));
        } catch (SecurityException ex) {
            loadPlaces(DEFAULT_LAT, DEFAULT_LNG, "Sin permiso de ubicación. Usando ubicación de referencia.");
        }
    }

    private void useLocationOrDefault(Location location) {
        if (location != null && isInMallorca(location.getLatitude(), location.getLongitude())) {
            loadPlaces(location.getLatitude(), location.getLongitude(), "Ubicación GPS de Mallorca");
        } else {
            String message = location == null
                    ? "GPS no disponible. Usando ubicación de referencia."
                    : "El GPS está fuera de Mallorca. Usando ubicación de referencia.";
            loadPlaces(DEFAULT_LAT, DEFAULT_LNG, message);
        }
    }

    private boolean isInMallorca(double lat, double lng) {
        // Caja geográfica amplia que cubre Mallorca y evita usar posiciones de otras islas o de la península.
        return lat >= 39.20 && lat <= 40.10 && lng >= 2.20 && lng <= 3.60;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted = true;
            }
            if (granted) {
                startLocationFlow();
            } else {
                loadPlaces(DEFAULT_LAT, DEFAULT_LNG, "Permiso GPS denegado. Usando ubicación de referencia.");
            }
        }
    }

    private void loadPlaces(double lat, double lng, String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            progressBar.setVisibility(View.VISIBLE);
            resultsContainer.removeAllViews();
            TextView loading = new TextView(this);
            loading.setText("Buscando lugares…");
            loading.setTextSize(16);
            loading.setPadding(dp(8), dp(18), dp(8), dp(18));
            resultsContainer.addView(loading);
        });

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String query = API_BASE
                        + "?lat=" + lat
                        + "&lng=" + lng
                        + "&idioma=es"
                        + "&categorias=" + URLEncoder.encode("Playa,Pueblo", "UTF-8")
                        + "&limite=2";
                URL url = new URL(query);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "MallorcaLikesYou-Android/1.0");
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder body = new StringBuilder();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    body.append(new String(buffer, 0, count, "UTF-8"));
                }
                stream.close();
                if (code < 200 || code >= 300) {
                    throw new Exception("El servidor respondió con HTTP " + code);
                }
                JSONObject response = new JSONObject(body.toString());
                if (!response.optBoolean("ok", false)) {
                    throw new Exception(response.optString("mensaje", "La API no ha podido completar la búsqueda."));
                }
                JSONArray places = response.optJSONArray("resultados");
                runOnUiThread(() -> showResults(places, lat, lng));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    resultsContainer.removeAllViews();
                    TextView error = new TextView(this);
                    error.setText("No se han podido cargar los lugares.\n\n" + e.getMessage()
                            + "\n\nComprueba la conexión e inténtalo de nuevo.");
                    error.setTextSize(16);
                    error.setPadding(dp(12), dp(20), dp(12), dp(20));
                    resultsContainer.addView(error);
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showResults(JSONArray places, double lat, double lng) {
        progressBar.setVisibility(View.GONE);
        resultsContainer.removeAllViews();
        if (places == null || places.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No se han encontrado lugares en esta búsqueda.");
            empty.setTextSize(16);
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            resultsContainer.addView(empty);
            return;
        }

        TextView count = new TextView(this);
        count.setText(places.length() + " lugares encontrados");
        count.setTextSize(14);
        count.setTypeface(null, 1);
        count.setTextColor(0xFF4B5555);
        count.setPadding(dp(4), dp(8), dp(4), dp(10));
        resultsContainer.addView(count);

        for (int i = 0; i < places.length(); i++) {
            JSONObject place = places.optJSONObject(i);
            if (place != null) addPlaceCard(place);
        }
    }

    private void addPlaceCard(JSONObject place) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackgroundColor(0xFFFFFFFF);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.setMargins(0, 0, 0, dp(14));
        resultsContainer.addView(card, cardParams);

        TextView name = new TextView(this);
        name.setText(place.optString("lugar", place.optString("nombre", "Lugar sin nombre")));
        name.setTextSize(21);
        name.setTextColor(0xFF183A39);
        name.setTypeface(null, 1);
        card.addView(name);

        String category = place.optString("categoria", "");
        String municipality = place.optString("municipio", "");
        double distance = place.optDouble("distancia_km", -1);
        String meta = category;
        if (!municipality.isEmpty()) meta += " · " + municipality;
        if (distance >= 0) meta += " · " + String.format(java.util.Locale.getDefault(), "%.2f km", distance);
        TextView details = new TextView(this);
        details.setText(meta);
        details.setTextSize(13);
        details.setTextColor(0xFF52706E);
        details.setPadding(0, dp(4), 0, dp(8));
        card.addView(details);

        JSONObject firstMedia = firstMedia(place);
        if (firstMedia != null) {
            String mediaUrl = mediaUrl(firstMedia);
            String author = mediaAuthor(firstMedia);
            if (isVideo(firstMedia, mediaUrl)) {
                TextView video = new TextView(this);
                video.setText("▶  Vídeo disponible");
                video.setGravity(Gravity.CENTER);
                video.setTextSize(18);
                video.setTextColor(0xFFFFFFFF);
                video.setBackgroundColor(0xFF294D4B);
                card.addView(video, new LinearLayout.LayoutParams(-1, dp(170)));
            } else if (!mediaUrl.isEmpty()) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(image, new LinearLayout.LayoutParams(-1, dp(180)));
                loadImage(mediaUrl, image);
            }
            TextView authorText = new TextView(this);
            authorText.setText("Autor: " + author);
            authorText.setTextSize(12);
            authorText.setTextColor(0xFF687574);
            authorText.setPadding(0, dp(5), 0, dp(8));
            card.addView(authorText);
        }

        TextView description = new TextView(this);
        description.setText(place.optString("desc_corta", place.optString("descripcion", "")));
        description.setTextSize(15);
        description.setTextColor(0xFF303838);
        description.setPadding(0, dp(4), 0, dp(8));
        card.addView(description);

        TextView open = new TextView(this);
        open.setText("Ver detalle y cómo llegar  ›");
        open.setTextSize(14);
        open.setTypeface(null, 1);
        open.setTextColor(0xFF087E78);
        open.setPadding(0, dp(8), 0, dp(2));
        card.addView(open);

        View.OnClickListener click = v -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("place_json", place.toString());
            startActivity(intent);
        };
        card.setOnClickListener(click);
        open.setOnClickListener(click);
    }

    private JSONObject firstMedia(JSONObject place) {
        JSONArray photos = place.optJSONArray("fotos");
        if (photos != null && photos.length() > 0) return photos.optJSONObject(0);
        JSONArray videos = place.optJSONArray("videos");
        if (videos != null && videos.length() > 0) return videos.optJSONObject(0);
        return null;
    }

    static String mediaUrl(JSONObject media) {
        if (media == null) return "";
        return media.optString("url", media.optString("src", media.optString("ruta", "")));
    }

    static String mediaAuthor(JSONObject media) {
        if (media == null) return "Paco Gómez 2025";
        String author = media.optString("autor", "").trim();
        return author.isEmpty() || author.equalsIgnoreCase("null") ? "Paco Gómez 2025" : author;
    }

    static boolean isVideo(JSONObject media, String url) {
        String type = media == null ? "" : media.optString("tipo", "").toLowerCase();
        String mime = media == null ? "" : media.optString("mime", "").toLowerCase();
        String lowerUrl = url == null ? "" : url.toLowerCase();
        return type.contains("video") || mime.contains("video") || lowerUrl.contains(".mp4");
    }

    private void loadImage(String imageUrl, ImageView target) {
        executor.execute(() -> {
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
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
