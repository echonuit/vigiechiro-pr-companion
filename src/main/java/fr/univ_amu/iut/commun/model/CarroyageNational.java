package fr.univ_amu.iut.commun.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/// Le **carroyage national Vigie-Chiro** (« carrenat »), embarqué : numéro de carré vers centroïde
/// WGS84 de sa maille, pour toute la France métropolitaine.
///
/// Le numéro **n'encode aucune coordonnée** - six chiffres valent département plus identifiant local -
/// d'où ce référentiel plutôt qu'un décodage. Embarqué par #325 pour dessiner l'emprise d'un carré, il
/// est descendu ici par #4621 : le découpage du territoire est une donnée du **domaine**, et un service
/// du modèle n'a pas à la chercher dans un paquet de vue.
///
/// La ressource vient de `cesco-lab/Vigie-Chiro_scripts inputs/CountryGrids/carrenatFR.csv`
/// (EPSG:27572 Lambert II étendu) convertie en WGS84.
public final class CarroyageNational {

    private static final String RESSOURCE = "carrenat.csv.gz";

    /// Au-delà de cette distance, aucune maille n'est candidate : la position est hors du carroyage.
    ///
    /// Une maille fait 2 km de côté, donc tout point qu'elle couvre est à moins de sa demi-diagonale de
    /// son centre, `1000 x racine(2)`, soit environ 1 414 m. Mesuré le 2026-08-27 : un coin est à
    /// 1 412 m de ses quatre centres. La marge porte le seuil à 1 500 m, ce qui laisse passer les coins
    /// sans admettre la maille d'à côté, dont le centre est à 2 km.
    private static final double PORTEE_METRES = 1_500;

    private static final double METRES_PAR_DEGRE_LAT = 111_132;

    private static final double METRES_PAR_DEGRE_LON_EQUATEUR = 111_320;

    private final Map<String, PositionGeo> centroides;

    private CarroyageNational(Map<String, PositionGeo> centroides) {
        this.centroides = centroides;
    }

    /// Le carroyage embarqué, chargé **une seule fois** : la grille nationale pèse 137 000 mailles et
    /// tous les appelants partagent la même table.
    public static CarroyageNational embarque() {
        return Porteur.INSTANCE;
    }

    /// Un carroyage lu depuis un contenu CSV donné, mêmes règles que la ressource embarquée.
    ///
    /// Existe pour que la lecture et la recherche s'éprouvent sur une grille de quelques mailles. Sans
    /// elle, la seule façon de tester cette classe serait de charger la grille nationale, et ni les
    /// lignes qu'elle écarte ni la borne de portée ne seraient atteignables.
    public static CarroyageNational depuis(String contenuCsv) {
        Map<String, PositionGeo> centroides = new HashMap<>();
        contenuCsv.lines().forEach(ligne -> lireUneLigne(ligne.strip(), centroides));
        return new CarroyageNational(centroides);
    }

    /// Centroïde de la maille `numeroCarre`, ou **vide** quand le numéro est inconnu : hors métropole,
    /// ou simplement faux.
    public Optional<PositionGeo> centroide(String numeroCarre) {
        return Optional.ofNullable(centroides.get(numeroCarre));
    }

    /// Les carrés **candidats** pour une position, du plus proche au plus éloigné (#4621).
    ///
    /// Le carré d'une position est la maille dont le centre est le plus proche : pour un réseau de
    /// mailles carrées, la partition par centre le plus proche **est** le découpage lui-même. Sur une
    /// frontière l'égalité est stricte, et départager n'a pas de sens ici : la liste les rend tous, et
    /// c'est l'appelant qui tranche ou renonce.
    ///
    /// Le carroyage ne couvre que la métropole. Une position hors d'elle rendrait sinon un carré à des
    /// centaines de kilomètres, plausible et faux.
    public List<CarreCandidat> candidats(double latitude, double longitude) {
        List<CarreCandidat> proches = new ArrayList<>();
        for (Map.Entry<String, PositionGeo> maille : centroides.entrySet()) {
            double distance = distanceMetres(latitude, longitude, maille.getValue());
            if (distance <= PORTEE_METRES) {
                proches.add(new CarreCandidat(maille.getKey(), distance));
            }
        }
        proches.sort(Comparator.comparingDouble(CarreCandidat::distanceMetres));
        return List.copyOf(proches);
    }

    /// Distance en mètres, par projection équirectangulaire locale. À l'échelle du kilomètre, elle
    /// s'écarte de la distance géodésique de bien moins d'un mètre, et les mesures du 2026-08-27 le
    /// confirment contre le serveur : 1 411,7 m calculés ici pour 1 412 m rendus par la plateforme.
    private static double distanceMetres(double latitude, double longitude, PositionGeo centre) {
        double dx =
                (centre.longitude() - longitude) * METRES_PAR_DEGRE_LON_EQUATEUR * Math.cos(Math.toRadians(latitude));
        double dy = (centre.latitude() - latitude) * METRES_PAR_DEGRE_LAT;
        return Math.hypot(dx, dy);
    }

    /// Nombre de mailles connues. Sert aux tests et au diagnostic.
    public int taille() {
        return centroides.size();
    }

    /// Porteur d'initialisation paresseuse : la table ne se lit qu'au premier appel.
    private static final class Porteur {
        private static final CarroyageNational INSTANCE = new CarroyageNational(charger());

        private Porteur() {}
    }

    private static Map<String, PositionGeo> charger() {
        try (InputStream flux = CarroyageNational.class.getResourceAsStream(RESSOURCE)) {
            // Ressource absente du paquetage : table vide, et l'appelant se replie. Défensif, et hors
            // d'atteinte d'un test sans truquer le chargeur de classes.
            return flux == null ? Map.of() : lireGzip(flux);
        } catch (IOException probleme) {
            throw new UncheckedIOException("Lecture du référentiel de carroyage " + RESSOURCE, probleme);
        }
    }

    /// Un carroyage lu depuis un flux **gzip**, même format que la ressource embarquée.
    ///
    /// Séparé de [#charger()] pour que la lecture du flux s'éprouve : le porteur statique ne charge
    /// qu'une fois par JVM, donc muter le chargement derrière lui ne change plus rien une fois la table
    /// en place, et les mutations y survivaient sans qu'aucun test ne soit en cause.
    public static CarroyageNational depuisGzip(InputStream fluxGzip) throws IOException {
        return new CarroyageNational(lireGzip(fluxGzip));
    }

    private static Map<String, PositionGeo> lireGzip(InputStream flux) throws IOException {
        Map<String, PositionGeo> centroides = new HashMap<>();
        try (BufferedReader lecteur =
                new BufferedReader(new InputStreamReader(new GZIPInputStream(flux), StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                lireUneLigne(ligne.strip(), centroides);
            }
        }
        return centroides;
    }

    private static void lireUneLigne(String ligne, Map<String, PositionGeo> centroides) {
        if (ligne.isEmpty() || ligne.startsWith("#") || ligne.startsWith("numero")) {
            return; // commentaires et en-tête
        }
        String[] champs = ligne.split(";");
        if (champs.length < 3) {
            return;
        }
        try {
            centroides.put(
                    champs[0].strip(),
                    new PositionGeo(Double.parseDouble(champs[1].strip()), Double.parseDouble(champs[2].strip())));
        } catch (NumberFormatException ligneInvalide) {
            // Ligne non conforme (en-tête inattendu, colonne non numérique) : ignorée.
        }
    }
}
