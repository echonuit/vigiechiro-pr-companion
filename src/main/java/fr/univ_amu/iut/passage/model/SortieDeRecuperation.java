package fr.univ_amu.iut.passage.model;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.util.Objects;

/// Le moment où une nuit **cesse d'être « Récupérée »** (#2581).
///
/// Une nuit rapatriée de Vigie-Chiro porte ses observations et son rattachement, mais pas son audio.
/// Quand la réactivation le lui rend, cette raison d'être à part disparaît : elle devient une nuit
/// déposée comme les autres, et le workflow la traite comme telle.
///
/// ## Pourquoi un collaborateur plutôt que deux champs de plus
///
/// [ServiceReactivationPassage] orchestre déjà une douzaine de collaborateurs. Lui ajouter un
/// [PassageDao] **et** un [MoteurWorkflowPassage] aurait allongé sa construction de deux crans pour un
/// geste qui tient en trois lignes - et surtout, ces deux-là n'auraient rien nommé. Ici, le nom dit ce
/// qui se passe.
public class SortieDeRecuperation {

    private final PassageDao passageDao;

    @Inject
    public SortieDeRecuperation(PassageDao passageDao) {
        this.passageDao = Objects.requireNonNull(passageDao, "passageDao");
    }

    /// Fait sortir la nuit de l'état « Récupéré », **si elle y était et si son audio est revenu**.
    ///
    /// Sans effet dans tous les autres cas, et c'est voulu :
    ///
    ///  - une nuit qui n'était pas récupérée n'a pas à changer de statut parce qu'on l'a réactivée ;
    ///  - une réactivation qui n'a **rien** rebranché laisse la nuit exactement où elle était. Promouvoir
    ///    sur une tentative infructueuse dirait « c'est réglé » d'une nuit toujours sans son, et lui
    ///    retirerait au passage la recommandation qui la désignait.
    ///
    /// @param audioRevenu vrai si la nuit dispose à présent d'au moins un fichier audio
    public void promouvoirSiRecuperee(Long idPassage, boolean audioRevenu) {
        if (!audioRevenu) {
            return;
        }
        passageDao
                .findById(idPassage)
                .filter(p -> p.statutWorkflow() == StatutWorkflow.RECUPERE)
                .ifPresent(p -> {
                    // Pas d'appel au moteur ici. Il y en avait un, censé « rester juge de la transition » -
                    // mais le filtre au-dessus garantit déjà que celle-ci est « Récupéré → Déposé », la seule
                    // que le moteur autorise. Il ne pouvait donc JAMAIS refuser : PIT l'a montré en le
                    // supprimant sans qu'aucun test bouge. Une garde qui ne peut pas se déclencher n'est pas
                    // une garde, c'est un commentaire qui coûte un appel - et qui laisse croire à une
                    // vérification là où il n'y en a pas. La règle vit dans MoteurWorkflowPassage, et
                    // MoteurWorkflowPassageTest la tient.
                    passageDao.update(new Passage(
                            p.id(),
                            p.numeroPassage(),
                            p.annee(),
                            p.dateEnregistrement(),
                            p.heureDebut(),
                            p.heureFin(),
                            p.parametresAcquisition(),
                            StatutWorkflow.DEPOSE,
                            p.verdictVerification(),
                            p.commentaire(),
                            p.donneesMeteo(),
                            p.deposeLe(),
                            p.idPoint(),
                            p.idEnregistreur(),
                            p.idCampagne()));
                });
    }
}
