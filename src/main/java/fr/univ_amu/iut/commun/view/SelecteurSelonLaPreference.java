package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.model.PreferenceDesignation;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Le dispositif de désignation choisi **au moment de désigner**, et non au moment de construire.
///
/// ## Ce que la version précédente cassait
///
/// [Selecteurs#pour] résolvait le dispositif tout de suite. Or les écrans appellent la fabrique dans
/// leur **constructeur**, pour garder le porteur en champ `final`. La préférence était donc lue à la
/// construction de l'écran, ce qui produisait deux défauts d'un seul geste :
///
/// - **basculer l'interrupteur ne changeait rien** avant un redémarrage, l'onglet ayant déjà fabriqué
///   son dispositif ;
/// - **construire un écran touchait `app_setting`**, si bien qu'un injecteur réel sans migration jouée
///   échouait à fournir l'onglet des réglages. C'est `ordre-alternatif` qui l'a dit, pas la batterie
///   locale.
///
/// C'est la leçon de `PreferenceConservation` (#3471), appliquée au port mais pas encore à son
/// appelant : lire à l'usage, jamais à la construction.
final class SelecteurSelonLaPreference implements SelecteurFichier {

    private final PreferenceDesignation preference;
    private final Supplier<Window> fenetre;

    SelecteurSelonLaPreference(PreferenceDesignation preference, Supplier<Window> fenetre) {
        this.preference = Objects.requireNonNull(preference, "preference");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    /// Le dispositif que le réglage désigne **maintenant**.
    ///
    /// Visible du paquet pour que [SelecteursTest] constate le choix : les deux dispositifs ouvrent un
    /// dialogue, et le natif ne se pilote pas sans écran.
    SelecteurFichier dispositifCourant() {
        return preference.dialogueDeLApplication()
                ? new SelecteurFichierEnFenetre(fenetre)
                : new SelecteurFichierJavaFx(fenetre);
    }

    @Override
    public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
        return dispositifCourant().choisirDossier(titre, dossierInitial);
    }

    @Override
    public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
        return dispositifCourant().choisirFichier(titre, dossierInitial, filtre);
    }

    @Override
    public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
        return dispositifCourant().enregistrerFichier(titre, nomPropose, filtre);
    }
}
