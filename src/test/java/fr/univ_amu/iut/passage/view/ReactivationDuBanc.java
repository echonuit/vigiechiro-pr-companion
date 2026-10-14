package fr.univ_amu.iut.passage.view;

import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Désigne à la fiche du passage affichée le dossier que « Réactiver ce passage » demandera, sans ouvrir
/// le dialogue natif (#6256).
///
/// Le sélecteur de la fiche n'est remplaçable que depuis ce paquet (#1431). Le banc de l'IHM, qui vit
/// ailleurs, passe par cette porte, comme il passe par `DesignationDuBanc` pour l'assistant d'import :
/// le geste reste celui de l'utilisateur, et la modale de réactivation s'ouvre pour de vrai.
public final class ReactivationDuBanc {

    private ReactivationDuBanc() {}

    /// Fait répondre `dossier` à la prochaine demande de dossier de `controleur`, la fiche à l'écran.
    public static void designer(Object controleur, Path dossier) {
        Objects.requireNonNull(dossier, "dossier");
        if (!(controleur instanceof PassageController fiche)) {
            throw new IllegalStateException("l'écran affiché n'est pas la fiche d'un passage : " + controleur);
        }
        fiche.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(dossier);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                throw new AssertionError("la réactivation demande un dossier, pas un fichier");
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("la réactivation lit une source : ce geste n'écrit aucun fichier");
            }
        });
    }
}
