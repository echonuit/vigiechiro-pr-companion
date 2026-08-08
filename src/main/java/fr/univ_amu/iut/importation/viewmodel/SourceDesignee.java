package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.ExtracteurZip;
import java.nio.file.Path;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;

/// Ce que l'utilisateur a **désigné** comme source d'import, et comment l'écran le nomme (#1490).
///
/// ## Pourquoi ce n'est pas le dossier de travail
///
/// Pour un `.zip`, l'import lit dans un temporaire d'extraction - un `import-zip-<horodatage>` sous le
/// workspace. C'est le bon dossier **de travail**, et c'était le mauvais à **afficher** : l'utilisateur
/// n'a pas choisi ce chemin, ne le reconnaît pas, et un chemin interne du workspace n'a rien à faire
/// sous ses yeux.
///
/// Les deux notions se ressemblaient assez pour n'en faire qu'une, et c'est ce qui a produit le défaut.
/// Elles vivent désormais dans deux objets.
public final class SourceDesignee {

    private final ObjectProperty<Path> origine = new SimpleObjectProperty<>(this, "origine");
    private final ReadOnlyStringWrapper libelle = new ReadOnlyStringWrapper(this, "libelle", "");

    public SourceDesignee() {
        origine.addListener((obs, ancien, nouveau) -> libelle.set(libelleDe(nouveau)));
    }

    /// Le chemin désigné : carte SD, dossier, ou archive.
    public ObjectProperty<Path> origineProperty() {
        return origine;
    }

    /// Le libellé à montrer : le chemin désigné, suivi de « (décompressé) » pour une archive. La mention
    /// explique pourquoi l'import lira ailleurs, sans exposer le chemin interne.
    public ReadOnlyStringProperty libelleProperty() {
        return libelle.getReadOnlyProperty();
    }

    private static String libelleDe(Path origine) {
        if (origine == null) {
            return "";
        }
        return ExtracteurZip.estZip(origine) ? origine + " (décompressé)" : origine.toString();
    }
}
