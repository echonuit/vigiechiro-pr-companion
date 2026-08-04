package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// Porteur **injectable** du choix d'une sauvegarde, quatrième du socle avec
/// [SelecteurFichierModifiable], [ConfirmateurModifiable] et [NotificateurModifiable] : la vraie
/// fenêtre par défaut ([ChoixSauvegardeJavaFx]), remplaçable par un double en test ([#definir]) - son
/// `showAndWait()` figerait TestFX headless.
public final class ChoixSauvegardeModifiable implements ChoixSauvegarde {

    private ChoixSauvegarde delegue;

    public ChoixSauvegardeModifiable(ChoixSauvegarde initial) {
        this.delegue = Objects.requireNonNull(initial, "choix");
    }

    @Override
    public Optional<Path> choisir(
            String titre, Path dossier, List<InventaireSauvegardes.Entree> entrees, Supplier<Optional<Path>> repli) {
        return delegue.choisir(titre, dossier, entrees, repli);
    }

    /// Remplace la stratégie de choix (double répondant dans les tests).
    public void definir(ChoixSauvegarde choix) {
        this.delegue = Objects.requireNonNull(choix, "choix");
    }
}
