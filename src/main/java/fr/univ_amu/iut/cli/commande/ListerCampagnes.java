package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lister-campagnes` (#2355) : liste les campagnes de suivi, de la plus récente à la plus ancienne.
/// Lecture pure de [ServiceCampagne]. Option `--json` pour une sortie scriptable.
@Command(name = "lister-campagnes", description = "Liste les campagnes de suivi.")
public final class ListerCampagnes implements Callable<Integer>, LectureSeule {

    @Option(names = "--json", description = "Émet la liste au format JSON plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServiceCampagne service;

    @Inject
    public ListerCampagnes(ServiceCampagne service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<Campagne> campagnes = service.listerCampagnes();
        if (json) {
            sortie.println(FormatJson.tableau(
                    campagnes.stream().map(ListerCampagnes::enObjet).toList()));
            return 0;
        }
        if (campagnes.isEmpty()) {
            sortie.println("Aucune campagne enregistrée.");
            return 0;
        }
        sortie.println(campagnes.size() + " campagne(s) :");
        for (Campagne campagne : campagnes) {
            sortie.println("  #" + campagne.id() + "  " + campagne.nom() + "  (" + campagne.annee() + ")"
                    + (campagne.commentaire() == null ? "" : "  - " + campagne.commentaire()));
        }
        return 0;
    }

    /// Projection JSON d'une campagne (clés stables pour les scripts).
    private static Map<String, Object> enObjet(Campagne campagne) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("id", campagne.id());
        objet.put("nom", campagne.nom());
        objet.put("annee", campagne.annee());
        objet.put("commentaire", campagne.commentaire());
        return objet;
    }
}
