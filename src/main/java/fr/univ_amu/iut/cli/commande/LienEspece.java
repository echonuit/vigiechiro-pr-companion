package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.LectureSeule;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.ConstructeurLienEspece;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Fiche d'une espèce (#1874), construite par le même service que l'interface graphique.
@Command(name = "lien-espece", description = "Afficher l'URL de la fiche d'une espèce du référentiel.")
public final class LienEspece implements Callable<Integer>, LectureSeule {
    @Option(
            names = "--code",
            required = true,
            paramLabel = "<code Tadarida>",
            description = "Code de l'espèce dans le référentiel local.")
    private String code;

    @Spec
    private CommandSpec spec;

    private final TaxonDao taxons;
    private final ConstructeurLienEspece liens;

    @Inject
    public LienEspece(TaxonDao taxons, ConstructeurLienEspece liens) {
        this.taxons = Objects.requireNonNull(taxons, "taxons");
        this.liens = Objects.requireNonNull(liens, "liens");
    }

    @Override
    public Integer call() {
        var taxon = taxons.findById(code).orElseThrow(() -> new ErreurUsage("Taxon inconnu : " + code + "."));
        var espece = new EspeceIdentifiee(taxon.code(), taxon.nomLatin(), taxon.nomVernaculaireFr());
        String url = liens.lienFiche(espece)
                .orElseThrow(() -> new ErreurUsage("Aucune fiche disponible pour le taxon " + code + "."));
        spec.commandLine().getOut().println(url);
        return 0;
    }
}
