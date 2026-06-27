package fr.univ_amu.iut.commun.view.carte;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/// Fournisseur d'emprise **officiel** (#325) : cale le carré sur le **carroyage national Vigie-Chiro**
/// (« carrenat »). Le numéro (NUMNAT = département + identifiant local) n'encode pas de coordonnées :
/// on lit le **centroïde WGS84** de la maille dans un référentiel embarqué (`carrenat.csv`), puis on en
/// déduit l'emprise du carré **2 km** centrée dessus.
///
/// Le référentiel embarqué (`carrenat.csv.gz`, gzip) couvre **toute la France métropolitaine**
/// (≈ 137 000 mailles), issu de `cesco-lab/Vigie-Chiro_scripts inputs/CountryGrids/carrenatFR.csv`
/// (EPSG:27572 Lambert II étendu) converti en WGS84. Un numéro absent (hors métropole, ou numéro
/// inconnu) renvoie `Optional.empty()` : la chaîne ([FournisseurEmpriseCarreEnChaine]) bascule alors sur
/// le repli [EmpriseAutourDesPoints].
public final class FournisseurEmpriseCarreOfficiel implements FournisseurEmpriseCarre {

    private static final String RESSOURCE = "carrenat.csv.gz";

    /// Demi-côté du carré Vigie-Chiro (2 km de côté) et conversion km → degrés (cf. [EmpriseAutourDesPoints]).
    private static final double DEMI_COTE_KM = 1.0;

    private static final double KM_PAR_DEGRE_LAT = 111.0;

    /// numéro de carré → centroïde `{latitude, longitude}` WGS84.
    private final Map<String, double[]> centroides;

    public FournisseurEmpriseCarreOfficiel() {
        this.centroides = chargerReferentiel();
    }

    @Override
    public Optional<EmpriseCarre> emprise(String numeroCarre, List<PointGeo> pointsDuCarre) {
        double[] centre = centroides.get(numeroCarre);
        if (centre == null) {
            return Optional.empty();
        }
        double latCentre = centre[0];
        double lonCentre = centre[1];
        double demiLat = DEMI_COTE_KM / KM_PAR_DEGRE_LAT;
        double demiLon = DEMI_COTE_KM / (KM_PAR_DEGRE_LAT * Math.cos(Math.toRadians(latCentre)));
        return Optional.of(
                new EmpriseCarre(latCentre - demiLat, lonCentre - demiLon, latCentre + demiLat, lonCentre + demiLon));
    }

    /// Nombre de carrés connus du référentiel (utile aux tests / au diagnostic).
    public int taille() {
        return centroides.size();
    }

    private static Map<String, double[]> chargerReferentiel() {
        Map<String, double[]> centroides = new HashMap<>();
        try (InputStream flux = FournisseurEmpriseCarreOfficiel.class.getResourceAsStream(RESSOURCE)) {
            if (flux == null) {
                return centroides; // pas de référentiel embarqué → tout passe au repli
            }
            try (BufferedReader lecteur =
                    new BufferedReader(new InputStreamReader(new GZIPInputStream(flux), StandardCharsets.UTF_8))) {
                String ligne;
                while ((ligne = lecteur.readLine()) != null) {
                    ligne = ligne.strip();
                    if (ligne.isEmpty() || ligne.startsWith("#") || ligne.startsWith("numero")) {
                        continue; // commentaires et en-tête
                    }
                    String[] champs = ligne.split(";");
                    if (champs.length >= 3) {
                        try {
                            centroides.put(champs[0].strip(), new double[] {
                                Double.parseDouble(champs[1].strip()), Double.parseDouble(champs[2].strip())
                            });
                        } catch (NumberFormatException ligneInvalide) {
                            // Ligne non conforme (en-tête inattendu, colonne non numérique) : ignorée.
                        }
                    }
                }
            }
        } catch (IOException probleme) {
            throw new UncheckedIOException("Lecture du référentiel de carroyage " + RESSOURCE, probleme);
        }
        return centroides;
    }
}
