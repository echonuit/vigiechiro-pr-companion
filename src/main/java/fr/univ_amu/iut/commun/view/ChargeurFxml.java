package fr.univ_amu.iut.commun.view;

import java.net.URL;
import java.util.Objects;
import javafx.fxml.FXMLLoader;

/// Fabrique un [FXMLLoader] dont la localisation est **garantie non nulle**, point d'entrée unique des
/// chargeurs de vue du socle et des features.
///
/// Les vues co-localisent leur `.fxml` à côté de leur classe dans `src/main/java/.../view/` (convention
/// TP3) ; Maven les recopie dans `target/classes`. Si un `target/classes` obsolète ne contient pas le
/// FXML (typiquement après un `git pull` qui ajoute une vue, sur un lancement qui court-circuite la
/// recopie Maven), `Class#getResource` renvoie `null` et `FXMLLoader#load()` échoue plus loin par un
/// cryptique `IllegalStateException: Location is not set`, sans indiquer quoi faire. Ce point d'entrée
/// transforme ce cas en message **actionnable** (reconstruire proprement) au moment exact de la
/// résolution de la ressource.
public final class ChargeurFxml {

    private ChargeurFxml() {}

    /// Construit un [FXMLLoader] pointant sur `nomFxml`, cherché à côté de `ancre` (mêmes règles de
    /// résolution que `ancre.getResource(nomFxml)`). Lève une [IllegalStateException] explicite si la
    /// ressource est absente du classpath, au lieu du « Location is not set » opaque de JavaFX.
    public static FXMLLoader chargeur(Class<?> ancre, String nomFxml) {
        Objects.requireNonNull(ancre, "ancre");
        Objects.requireNonNull(nomFxml, "nomFxml");
        URL emplacement = ancre.getResource(nomFxml);
        if (emplacement == null) {
            throw new IllegalStateException("Ressource FXML introuvable sur le classpath : « " + nomFxml
                    + " » (attendue à côté de " + ancre.getName() + "). Le dossier target/classes est"
                    + " probablement obsolète après un « git pull » : reconstruisez avec"
                    + " « ./mvnw clean javafx:run ».");
        }
        return new FXMLLoader(emplacement);
    }
}
