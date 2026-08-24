package fr.univ_amu.iut.commun.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// Applique une [ActionGroupee] à une suite de passages (#2357), **un à la fois**. **Séquentiel** :
/// les gestes enchaînés parallélisent déjà en interne, et lancer N passages multiplierait ce plafond.
///
/// **L'annulation s'arrête entre deux passages** - le jeton est consulté avant chacun, jamais
/// pendant -, un passage non commencé étant rendu [IssueTraitement.Statut#NON_TRAITE] : chacun est
/// dans son état d'avant ou d'après, ce qui rend un lot interrompu reprenable. **Un échec n'arrête
/// pas le lot** : l'action qui lève est enregistrée avec son motif. **Le motif est rédigé par la
/// surface** (ADR 2635) : `getMessage()` ne garderait que le fait, et treize lignes diraient « pas
/// connectée » sans dire où se reconnecter.
public final class MoteurTraitementGroupe {

    /// Journal muet, pour les appelants qui n'en veulent pas (tests, CLI silencieux).
    private static final Consumer<String> SANS_JOURNAL = ligne -> {};

    private final Function<RuntimeException, String> redactionEchec;

    /// Sans rédaction de surface : le fait seul, ce qui reste juste, seulement moins guidant.
    public MoteurTraitementGroupe() {
        this(MoteurTraitementGroupe::faitSeul);
    }

    /// @param redactionEchec ce qui transforme une exception en la phrase que l'observateur lira ;
    ///     `GesteAttendu::message` côté application
    public MoteurTraitementGroupe(Function<RuntimeException, String> redactionEchec) {
        this.redactionEchec = Objects.requireNonNull(redactionEchec, "redactionEchec");
    }

    /// Exécute `action` sur chaque cible, sans journal.
    public ResultatTraitementGroupe executer(ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton) {
        return executer(action, cibles, jeton, SANS_JOURNAL);
    }

    /// Exécute `action` sur chaque cible et rend le sort de chacune.
    ///
    /// Chaque ligne remise à `journal` est **préfixée du passage concerné** : sans cela, un lot
    /// interrompu est indiagnostiquable : on sait qu'il a échoué, pas où.
    ///
    /// @param jeton consulté avant chaque passage ; une fois annulé, les passages restants sont rendus
    ///     `NON_TRAITE` sans être touchés
    /// @return une issue par cible, dans l'ordre de soumission
    public ResultatTraitementGroupe executer(
            ActionGroupee action, List<CiblePassage> cibles, JetonAnnulation jeton, Consumer<String> journal) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(cibles, "cibles");
        Objects.requireNonNull(jeton, "jeton");
        Objects.requireNonNull(journal, "journal");

        List<IssueTraitement> issues = new ArrayList<>();
        boolean interrompu = false;
        for (CiblePassage cible : cibles) {
            if (jeton.estAnnule()) {
                interrompu = true;
                issues.add(IssueTraitement.nonTraite(cible));
                continue;
            }
            issues.add(traiter(action, cible, jeton, journal));
        }
        return new ResultatTraitementGroupe(action.libelle(), issues, interrompu);
    }

    private IssueTraitement traiter(
            ActionGroupee action, CiblePassage cible, JetonAnnulation jeton, Consumer<String> journal) {
        var motifNonEligible = action.motifNonEligible(cible);
        if (motifNonEligible.isPresent()) {
            journal.accept(prefixe(cible) + "écarté : " + motifNonEligible.get());
            return IssueTraitement.ecarte(cible, motifNonEligible.get());
        }
        journal.accept(prefixe(cible) + action.libelle() + "…");
        try {
            action.executer(cible, jeton);
            journal.accept(prefixe(cible) + "terminé");
            return IssueTraitement.reussi(cible);
        } catch (RuntimeException echec) {
            String motif = redactionEchec.apply(echec);
            journal.accept(prefixe(cible) + "ÉCHEC : " + motif);
            return IssueTraitement.echec(cible, motif);
        }
    }

    /// Le fait, sans le geste. Le nom de la classe sert de dernier recours : une exception sans message
    /// laisserait une ligne vide, ce qui se lit comme « rien à signaler ».
    private static String faitSeul(RuntimeException echec) {
        return echec.getMessage() == null ? echec.getClass().getSimpleName() : echec.getMessage();
    }

    /// Préfixe de journal identifiant le passage (critère d'acceptation de #2357).
    private static String prefixe(CiblePassage cible) {
        return "[" + cible.designation() + "] ";
    }
}
