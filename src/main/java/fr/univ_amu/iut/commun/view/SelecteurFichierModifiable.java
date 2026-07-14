package fr.univ_amu.iut.commun.view;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/// Porteur **injectable** de la désignation de fichier, exact pendant de [ConfirmateurModifiable] et de
/// [NotificateurModifiable] : par défaut les vrais sélecteurs natifs ([SelecteurFichierJavaFx]),
/// remplaçables par un double en test ([#definir]) - un sélecteur natif figerait TestFX headless.
///
/// L'action en détient une instance `final` et l'expose à ses tests, comme elle le fait déjà du
/// confirmateur et du notificateur. Les trois porteurs vont ensemble : une action qui **demande un
/// fichier**, **confirme**, puis **rend compte** n'est testable que si les trois dialogues sont
/// remplaçables. Il en manquait un, et il suffisait à bloquer le geste.
public final class SelecteurFichierModifiable implements SelecteurFichier {

    private SelecteurFichier delegue;

    /// Porteur aux sélecteurs natifs par défaut.
    public SelecteurFichierModifiable(SelecteurFichier initial) {
        this.delegue = Objects.requireNonNull(initial, "selecteur");
    }

    @Override
    public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
        return delegue.choisirDossier(titre, dossierInitial);
    }

    @Override
    public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
        return delegue.choisirFichier(titre, dossierInitial, filtre);
    }

    /// Remplace la stratégie de désignation (double répondant dans les tests).
    public void definir(SelecteurFichier selecteur) {
        this.delegue = Objects.requireNonNull(selecteur, "selecteur");
    }
}
