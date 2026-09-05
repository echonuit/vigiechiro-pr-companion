package fr.univ_amu.iut.commun.view;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Implémentation de [SelecteurFichier] par une **fenêtre de l'application**, et non par le dialogue
/// du système (changement `selecteur-de-repli`, ADR 5307).
///
/// C'est ici que vit le `showAndWait()`, et c'est pourquoi le port existe : dans un écran il figerait
/// un test headless, dans l'implémentation d'un port il est légitime. Le socle en compte huit du même
/// genre, dont [ChoixSauvegardeJavaFx], qui est le patron suivi ici.
///
/// **Le contrat ne change pas.** Les trois méthodes rendent le chemin désigné ou rien, exactement
/// comme [SelecteurFichierJavaFx]. C'est ce qui rend le choix entre les deux sans danger, et aucune
/// des vingt-cinq actions appelantes n'a à savoir laquelle répond.
public final class SelecteurFichierEnFenetre implements SelecteurFichier {

    private final Supplier<Window> fenetre;

    public SelecteurFichierEnFenetre(Supplier<Window> fenetre) {
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public Optional<Path> choisirDossier(String titre, Optional<Path> dossierInitial) {
        return demander(ContenuDesignation.Mode.DOSSIER, titre, dossierInitial.orElse(null), null, null);
    }

    @Override
    public Optional<Path> choisirFichier(String titre, Optional<Path> dossierInitial, FiltreFichier filtre) {
        return demander(ContenuDesignation.Mode.FICHIER, titre, dossierInitial.orElse(null), null, filtre);
    }

    @Override
    public Optional<Path> enregistrerFichier(String titre, String nomPropose, FiltreFichier filtre) {
        return demander(ContenuDesignation.Mode.ENREGISTREMENT, titre, null, nomPropose, filtre);
    }

    /// Ouvre la fenêtre, attend, et rend ce qui a été désigné.
    ///
    /// Une **seule** fenêtre est ouverte, et elle se ferme avant que l'appelant reprenne la main. Le
    /// socle refuse deux modales empilées pour un seul geste (#2642), et c'est le même geste que
    /// [ChoixSauvegardeJavaFx] : fermer d'abord, rendre ensuite.
    private Optional<Path> demander(
            ContenuDesignation.Mode mode, String titre, Path depart, String nomPropose, FiltreFichier filtre) {
        Window proprietaire = fenetre.get();
        Stage modale = new Stage();
        modale.initOwner(proprietaire);
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titre);

        AtomicReference<Path> designe = new AtomicReference<>();
        ContenuDesignation contenu = new ContenuDesignation(
                mode,
                depart == null ? Path.of(System.getProperty("user.home")) : depart,
                nomPropose,
                filtre,
                chemin -> {
                    designe.set(chemin);
                    modale.close();
                },
                modale::close);

        modale.setScene(Habillage.scene(contenu.racine(), 720, 520));
        Modales.fermerParEchap(modale);
        if (proprietaire != null) {
            Modales.centrerSur(modale, proprietaire);
        }
        modale.showAndWait();
        return Optional.ofNullable(designe.get());
    }
}
