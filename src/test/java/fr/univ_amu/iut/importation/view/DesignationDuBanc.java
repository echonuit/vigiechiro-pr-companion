package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.Confirmateur;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Désigne une carte SD à l'assistant d'import affiché, sans ouvrir ni le dialogue natif ni une modale.
///
/// Le sélecteur de l'assistant n'est remplaçable que depuis ce paquet (#1431). Un banc qui vit ailleurs,
/// comme le banc de l'IHM (#105), passe par cette porte plutôt que par la réflexion : le geste reste
/// celui des scénarios d'import, et le dialogue natif, qui figerait le banc, ne s'ouvre jamais.
public final class DesignationDuBanc {

    private DesignationDuBanc() {}

    /// Fait répondre `carte` à la prochaine demande de dossier de `controleur`, l'assistant à l'écran,
    /// et `confirmateur` à chacune de ses confirmations (nuit déjà importée, écrasement) : un banc ne
    /// laisse jamais l'assistant ouvrir une modale qu'il faudrait cliquer.
    public static void designer(Object controleur, Path carte, Confirmateur confirmateur) {
        Objects.requireNonNull(carte, "carte");
        if (!(controleur instanceof ImportationController assistant)) {
            throw new IllegalStateException("l'écran affiché n'est pas l'assistant d'import : " + controleur);
        }
        assistant.confirmateur().definir(Objects.requireNonNull(confirmateur, "confirmateur"));
        assistant.selecteur().definir(new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
                return Optional.of(carte);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
                throw new AssertionError("l'import LIT une source : ce geste n'écrit aucun fichier");
            }
        });
    }
}
