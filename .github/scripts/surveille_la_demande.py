#!/usr/bin/env python3
"""Surveille une demande, et DIT quand il n a pas pu lire au lieu de sortir en 0 (#5621).

    python3 .github/scripts/surveille_la_demande.py <depot> <numero> [budget-en-secondes]
    python3 .github/scripts/surveille_la_demande.py --auto-test

## Le defaut que ce script existe pour ne plus reecrire

`ouvrir-une-pr` prescrit de sortir en `0` quel que soit le verdict, et elle a raison pour ce qu elle
vise : `gh pr checks` sort NON NUL des qu une verification echoue, donc un moniteur qui s arrete sur
ce code meurt exactement quand il servait.

Mais la regle ne distingue pas **j ai lu un verdict** de **je n ai pas pu lire**. Deux moniteurs
ecrits a la main le 2026-09-30, par deux sessions qui ne se parlaient pas, portaient chacun une moitie
du defaut :

    le mien        sortait en 0 sur une panne reseau, et son journal se terminait
                   comme une surveillance conclue - la demande etait verte, 26 SUCCESS,
                   et je ne le savais pas par lui
    celui du pair  rendait [] sur un echec reseau, donc un compte de zero, donc
                   « toujours en cours apres 27 min » - pas de faux termine, mais
                   « je n ai pas pu lire » s y lisait « ca tourne encore »

Leur protection etait ACCIDENTELLE et ils l ecrivaient : le garde `-gt 0` etait la pour ne pas
conclure sur une liste vide au demarrage, avant que la forge ait cree les check-runs. Qu il attrape
aussi la panne reseau etait une coincidence, et une pratique qu on ne s est pas formulee ne se rejoue
pas a volonte.

## Trois issues, donc trois codes

    0   CONCLU           toutes les verifications ont rendu un verdict, QUELLE QUE SOIT leur couleur
    3   PAS CONCLU       le budget est epuise et il en restait en cours
    4   PAS PU LIRE      trois lectures de suite n ont rien rendu, ou `gh` est absent

Un rouge sort donc en **0** : c est un verdict, et l intention d origine tient. Ne pas avoir lu n est
pas un verdict, et c est la seule chose que ce script ajoute a la regle.

## Une demande SANS verification encore n est pas une panne

Tant que la forge n a pas cree les check-runs, `gh pr checks --json` ne rend PAS `[]` : il sort en 1,
n ecrit RIEN sur sa sortie, et dit `no checks reported on the '<branche>' branch` sur l erreur. Cette
page affirmait le contraire jusqu a #5976, et son cas d auto-test fabriquait un `[]` que `gh` ne
produit pas a ce moment-la. Le script lisait donc une panne, et rendait la main en 4 a la seconde ou
la surveillance commence : vecu sur #5914, puis six fois le 2026-10-06, dont deux sur #6053 ou la
fenetre sans verification a dure cinq minutes.

La marque sur l erreur est une lecture REUSSIE qui dit « rien encore », donc `en cours`, et surtout
pas `illisible`. La lecture des verifications la traduit en liste vide, et elle SEULE la passe a
`_lit`. Toute AUTRE erreur reste une panne, sans quoi une coupure reseau redeviendrait une attente
(#5621) : un cas le tient. La tete et les ateliers attendent un objet et non une liste, donc une
liste vide y resterait une panne meme si la marque leur parvenait ; aucun cas ne le dit, parce
qu aucune mutation de cette lecture ne pourrait le faire rougir.

**Ce que cela fait du budget** : une demande qui n aura jamais de verification, parce qu elle est en
conflit, n est plus quittee en dix secondes. Elle est sondee jusqu au bout du budget, 1800 s par
defaut, puis rendue en 3 avec « aucune verification creee ».

## Un ensemble PARTIELLEMENT CREE ressemble a un ensemble conclu

Troisieme forme du meme defaut, et celle-ci m a fait conclure faux en m en servant pour de bon. Le
moniteur a lu **7 verifications, toutes vertes**, et a dit CONCLU. La demande en portait **25, dont
17 en attente** : la forge cree ses check-runs PROGRESSIVEMENT, et zero en attente parmi sept crees
n est pas zero en attente.

La liste vide etait le cas facile, et le traiter seul laissait le cas difficile intact : une liste
PARTIELLE est non vide, donc elle passait le garde `if checks`.

**Le signal qui tranche n est pas dans les verifications, il est dans les EXECUTIONS d atelier.**
Tant qu une execution de la tete est `queued` ou `in_progress`, d autres verifications peuvent
apparaitre. Mesure faite au moment ou le moniteur concluait faux :

    gh pr checks                         26 verifications
    actions/runs?head_sha=<tete>         8 completed, 1 in_progress, 1 queued

Le moniteur conclut donc a DEUX conditions : aucune verification en attente, ET aucune execution en
vol. La seconde est celle qui manquait.

**Sa limite, declaree plutot que decouverte** : elle repond pour les ateliers de la forge. Une
verification tierce, creee par un service exterieur, pourrait encore arriver apres. Le depot n en a
aucune aujourd hui - les 26 verifications de la derniere demande viennent toutes d executions
d atelier - et si cela change, ce paragraphe est faux.

## Ce qu il ne fait pas

**Il ne juge pas la couleur.** Conclure que la demande est verte, et decider de fusionner, reste le
travail de qui lit. Ce script repond a « les verifications ont-elles fini » et a rien d autre.

**Il ne reprend pas indefiniment.** Trois tentatives par lecture, comme
`mesure_duree_portail.insiste` : au-dela, une API qui bafouille trois fois de suite n est plus un
hoquet, et le dire vaut mieux que d attendre.
"""

from __future__ import annotations

import json
import os
import pathlib
import subprocess
import sys
import time

CONCLU = 0
PAS_CONCLU = 3
PAS_PU_LIRE = 4

BUDGET_PAR_DEFAUT = 1800
PAUSE_ENTRE_SONDES = 30
PAUSE_DE_REPRISE = 3
TENTATIVES = 3

# ⟨les etats que la forge rend⟩ `gh pr checks --json bucket` range chaque verification dans l un de
# ces cinq. Seul `pending` n est pas un verdict ; `skipping` en est un, et le compter comme un echec
# ferait crier sur des demandes saines - c est le second piege que `ouvrir-une-pr` nomme deja.
EN_ATTENTE = "pending"

# ⟨la marque de `gh` sur une demande sans verification (#5976)⟩ Litteral ANGLAIS, non localise, lu
# sur la sortie d ERREUR. Releve avec gh 2.97.0 le 2026-10-06 : sur #6053, 28 secondes apres son
# ouverture, et sur des demandes fermees sans verification (#3928, #3353). Si une version de `gh`
# reformule ce message, la lecture redevient une panne et le moniteur sort en 4 : il se tait trop
# tot, il ne conclut jamais a tort.
AUCUNE_VERIFICATION = "no checks reported"


def _lit(arguments: list[str], lanceur, dors, vide_si: str | None = None) -> object | None:
    """Le JSON rendu par `gh`, ou `None` quand on n a PAS PU LIRE. Trois tentatives.

    Un seul endroit lit la forge, parce que les deux interrogations de ce script doivent refuser de
    la MEME facon : deux refus ecrits a deux endroits divergent a la premiere reformulation.

    `vide_si` nomme une marque de la sortie d ERREUR qui vaut « rien encore » : sortie vide et marque
    presente rendent une liste vide, sans reprise, parce que c est une reponse et non un hoquet.
    Seul qui sait qu une telle reponse existe le passe.
    """
    for essai in range(1, TENTATIVES + 1):
        # ⟨`gh` ABSENT leve, et `check=False` ne le couvre pas⟩ `check=False` parle du code de sortie ;
        # un executable introuvable fait lever `subprocess.run` avant qu il y ait un code. Le meme
        # defaut vivait dans `mesure_duree_portail` et `mesure_minutes_par_pr`, repare dans les deux,
        # et il reste dans six appels du depot (#5692).
        try:
            rendu = lanceur(arguments, capture_output=True, text=True, check=False)
        except OSError:
            return None
        # ⟨on juge sur le CONTENU, jamais sur le code⟩ `gh pr checks` sort non nul des qu une
        # verification echoue, ET rend son JSON. Lire le code ici ferait prendre une demande rouge
        # pour une panne de lecture, ce qui est exactement le defaut a l envers.
        if rendu.stdout.strip():
            try:
                return json.loads(rendu.stdout)
            except json.JSONDecodeError:
                pass
        elif vide_si and vide_si in (rendu.stderr or ""):
            return []
        if essai < TENTATIVES:
            dors(PAUSE_DE_REPRISE)
    return None


def interroge(depot: str, numero: int, lanceur=subprocess.run, dors=time.sleep) -> list | None:
    """Les verifications de la demande, ou `None` quand on n a PAS PU LIRE.

    Le `lanceur` est une couture : sans elle, les cas de ce script exigeraient le reseau, et un
    dispositif dont les cas ne tournent pas hors ligne ne se relance jamais.
    """
    rendu = _lit(
        ["gh", "pr", "checks", str(numero), "--repo", depot, "--json", "name,bucket"],
        lanceur,
        dors,
        vide_si=AUCUNE_VERIFICATION,
    )
    return rendu if isinstance(rendu, list) else None


def tete(depot: str, numero: int, lanceur=subprocess.run, dors=time.sleep) -> str | None:
    """Le SHA de la tete de la demande, ou `None`. Lu une fois, il ancre la question suivante."""
    rendu = _lit(
        ["gh", "pr", "view", str(numero), "--repo", depot, "--json", "headRefOid"], lanceur, dors
    )
    return rendu.get("headRefOid") if isinstance(rendu, dict) else None


def ateliers_en_vol(depot: str, sha: str, lanceur=subprocess.run, dors=time.sleep) -> int | None:
    """Combien d executions d atelier de cette tete ne sont pas `completed`, ou `None`.

    C est LE signal qui manquait : tant qu une execution est `queued` ou `in_progress`, la forge peut
    encore creer des verifications, et « zero en attente » ne veut rien dire.
    """
    rendu = _lit(
        ["gh", "api", f"repos/{depot}/actions/runs?head_sha={sha}&per_page=100"], lanceur, dors
    )
    if not isinstance(rendu, dict) or "workflow_runs" not in rendu:
        return None
    return sum(1 for r in rendu["workflow_runs"] if r.get("status") != "completed")


def en_cours(checks: list) -> int:
    """Combien n ont pas encore rendu de verdict. Une liste vide en rend zero, et c est voulu."""
    return sum(1 for c in checks if c.get("bucket") == EN_ATTENTE)


def composition(checks: list) -> str:
    """Le compte par etat, pour que le journal dise ce qui a ete lu et pas seulement combien."""
    par_etat: dict[str, int] = {}
    for c in checks:
        par_etat[c.get("bucket", "?")] = par_etat.get(c.get("bucket", "?"), 0) + 1
    return ", ".join(f"{n} {etat}" for etat, n in sorted(par_etat.items())) or "aucune"


def surveille(
    depot: str,
    numero: int,
    budget: int = BUDGET_PAR_DEFAUT,
    pause: int = PAUSE_ENTRE_SONDES,
    lire=None,
    en_vol=None,
    dors=time.sleep,
) -> int:
    """Sonde jusqu a conclusion, epuisement du budget, ou echec de lecture.

    `lire` rend les verifications, `en_vol` le nombre d executions d atelier non terminees. Les deux
    sont des coutures, et les deux doivent pouvoir rendre `None` : ne pas avoir lu l un des deux est
    ne pas avoir lu.
    """
    lire = lire or (lambda: interroge(depot, numero))
    if en_vol is None:
        # ⟨la tete se relit a CHAQUE sonde, et ce n est pas du gaspillage⟩ Une branche peut etre
        # repoussee pendant la surveillance - un rebase, une correction. Lue une seule fois, la tete
        # ferait juger les executions d un commit DEPASSE pendant que `gh pr checks` suit deja le
        # nouveau : le moniteur conclurait sur un etat qui n existe plus. Deux appels par sonde sont
        # le prix de repondre a « la CI de la tete ACTUELLE a-t-elle conclu ».
        def en_vol():
            sha = tete(depot, numero)
            return None if sha is None else ateliers_en_vol(depot, sha)

    ecoule = 0
    while True:
        checks = lire()
        restants = en_vol()
        if checks is None or restants is None:
            print(
                f"JE N AI PAS CONCLU : {TENTATIVES} lectures de suite n ont rien rendu "
                f"(« gh » absent, ou la forge muette). Le verdict de #{numero} reste inconnu.",
                file=sys.stderr,
            )
            return PAS_PU_LIRE

        attente = en_cours(checks)
        print(
            f"[{ecoule:5d}s] {len(checks)} verification(s) : {composition(checks)}"
            f" | {restants} atelier(s) en vol",
            flush=True,
        )

        # ⟨DEUX conditions, et la seconde est celle qui manquait⟩ Zero en attente parmi SEPT crees
        # n est pas zero en attente : la forge cree ses check-runs progressivement, et un ensemble
        # partiel est non vide donc il passait le garde `if checks`. Vecu sur la demande de ce lot,
        # ou ce moniteur a annonce CONCLU sur 7 vertes quand il y en avait 25 dont 17 en attente.
        if checks and attente == 0 and restants == 0:
            print(f"CONCLU : {composition(checks)}", flush=True)
            return CONCLU

        if ecoule + pause > budget:
            quoi = (
                f"{attente} en attente, {restants} atelier(s) en vol"
                if checks
                else "aucune verification creee"
            )
            print(
                f"JE N AI PAS CONCLU : budget de {budget} s epuise, {quoi}. "
                f"Le verdict de #{numero} reste inconnu.",
                file=sys.stderr,
            )
            return PAS_CONCLU

        dors(pause)
        ecoule += pause


def _serie(reponses: list) -> object:
    """Un lecteur qui rend les reponses donnees, puis redonne la derniere. Pour les cas."""
    restantes = list(reponses)

    def lire():
        return restantes.pop(0) if len(restantes) > 1 else restantes[0]

    return lire


def _auto_test() -> int:
    sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[2] / "scripts"))
    from _commun import cas_d_auto_test

    verifie, echecs = cas_d_auto_test()

    VERT = [{"name": "build", "bucket": "pass"}]
    ROUGE = [{"name": "build", "bucket": "fail"}, {"name": "lint", "bucket": "pass"}]
    ENCOURS = [{"name": "build", "bucket": EN_ATTENTE}]

    def dors(_):
        return None

    def rien_en_vol():
        return 0

    # ⟨LES TROIS ISSUES, une par code⟩ C est la partition entiere, et chacune doit etre atteinte par
    # un chemin different : sans les trois, ce script n aurait fait qu inverser le defaut d origine.
    verifie(
        "une demande conclue rend 0",
        lambda: surveille("d", 1, lire=_serie([VERT]), en_vol=rien_en_vol, dors=dors),
        CONCLU,
    )
    verifie(
        "une demande ROUGE conclue rend 0 aussi : un rouge est un verdict",
        lambda: surveille("d", 1, lire=_serie([ROUGE]), en_vol=rien_en_vol, dors=dors),
        CONCLU,
    )
    verifie(
        "ne pas avoir pu lire les verifications rend 4",
        lambda: surveille("d", 1, lire=_serie([None]), en_vol=rien_en_vol, dors=dors),
        PAS_PU_LIRE,
    )
    verifie(
        "ne pas avoir pu lire les ATELIERS rend 4 aussi : les deux lectures comptent",
        lambda: surveille("d", 1, lire=_serie([VERT]), en_vol=_serie([None]), dors=dors),
        PAS_PU_LIRE,
    )
    verifie(
        "un budget epuise sur une demande en cours rend 3",
        lambda: surveille(
            "d", 1, budget=60, pause=30, lire=_serie([ENCOURS]), en_vol=rien_en_vol, dors=dors
        ),
        PAS_CONCLU,
    )

    # ⟨LE CAS QUI M A FAIT CONCLURE FAUX⟩ Sept vertes et zero en attente, mais la forge cree encore
    # ses check-runs. Sans ce cas, le remede serait vert et le defaut intact : c est la seule chose
    # que ce lot a apprise en se servant de lui-meme.
    verifie(
        "sept vertes pendant qu un atelier est EN VOL ne conclut pas",
        lambda: surveille(
            "d", 1, budget=60, pause=30, lire=_serie([VERT]), en_vol=_serie([1]), dors=dors
        ),
        PAS_CONCLU,
    )
    verifie(
        "et des que l atelier a fini, la meme liste conclut",
        lambda: surveille("d", 1, lire=_serie([VERT]), en_vol=_serie([1, 0]), dors=dors),
        CONCLU,
    )

    # ⟨la liste VIDE, qui est le cas que la protection accidentelle couvrait⟩ Elle ne doit etre ni
    # une conclusion ni une panne : la forge n a pas encore cree les check-runs.
    verifie(
        "une liste vide au demarrage n est pas une conclusion",
        lambda: surveille(
            "d", 1, budget=60, pause=30, lire=_serie([[]]), en_vol=rien_en_vol, dors=dors
        ),
        PAS_CONCLU,
    )
    verifie(
        "et elle n est pas une panne non plus : la sonde suivante conclut",
        lambda: surveille("d", 1, lire=_serie([[], VERT]), en_vol=rien_en_vol, dors=dors),
        CONCLU,
    )

    # ⟨les lectures elles-memes, par une COUTURE et non par le reseau⟩ Les cas ci-dessus remplacent
    # les lecteurs ; ceux-ci les exercent, sans quoi rien ne prouverait que leurs branches repondent.
    class Rendu:
        def __init__(self, code, sortie, erreur=""):
            self.returncode, self.stdout, self.stderr = code, sortie, erreur

    verifie(
        "gh NON NUL avec du JSON valide est une LECTURE, pas une panne",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(1, json.dumps(ROUGE)), dors=dors),
        ROUGE,
    )
    verifie(
        "gh qui rend du vide trois fois de suite est une panne",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(0, ""), dors=dors),
        None,
    )
    # ⟨ce `[]` est FABRIQUE : `gh` ne le rend pas a l ouverture (#5976)⟩ Le chemin JSON reste vrai et
    # ce cas le tient, mais il ne dit rien du moment ou la demande vient de s ouvrir.
    verifie(
        "gh qui rend [] est une lecture reussie, et rend []",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(0, "[]"), dors=dors),
        [],
    )

    # ⟨CE QUE `gh` REND VRAIMENT sur une demande sans verification (#5976)⟩ Releve le 2026-10-06 sur
    # #6053, 28 secondes apres son ouverture, gh 2.97.0 : code 1, RIEN sur la sortie, et cette ligne
    # sur l erreur. Le moniteur y lisait une panne et rendait la main en 4, deux fois de suite.
    PAS_ENCORE = "no checks reported on the 'fix/4837-elision-un-seul-motif' branch\n"
    verifie(
        "une demande SANS verification encore est une lecture reussie, et rend []",
        lambda: interroge("d", 1, lanceur=lambda *a, **k: Rendu(1, "", PAS_ENCORE), dors=dors),
        [],
    )
    # Le controle apparie : sans lui, le remede serait de lire TOUTE erreur comme une attente, donc
    # de recreer le defaut que ce script existe pour ne plus ecrire (#5621).
    verifie(
        "une AUTRE erreur sur la sortie d erreur reste une panne",
        lambda: interroge(
            "d",
            1,
            lanceur=lambda *a, **k: Rendu(1, "", "error connecting to api.github.com\n"),
            dors=dors,
        ),
        None,
    )
    # Et de bout en bout, par la VRAIE lecture : la marque d abord, puis les verifications creees.
    # ⟨la marque est rendue AUTANT de fois qu une lecture a de tentatives⟩ Rendue une seule fois, ce
    # cas restait vert sans le remede : la reprise de `_lit` consommait la reponse suivante de la
    # serie, et la lecture « reussissait » au deuxieme essai. Vu en retirant le remede.
    de_l_ouverture = _serie([Rendu(1, "", PAS_ENCORE)] * TENTATIVES + [Rendu(0, json.dumps(VERT))])
    verifie(
        "le moniteur lance a l ouverture ATTEND, puis conclut quand les verifications arrivent",
        lambda: surveille(
            "d",
            1,
            lire=lambda: interroge("d", 1, lanceur=lambda *a, **k: de_l_ouverture(), dors=dors),
            en_vol=rien_en_vol,
            dors=dors,
        ),
        CONCLU,
    )
    verifie(
        "une tete illisible rend 4 : on ne juge pas les ateliers d un commit inconnu",
        lambda: surveille("d", 1, lire=_serie([VERT]), en_vol=lambda: None, dors=dors),
        PAS_PU_LIRE,
    )
    verifie(
        "la tete se lit dans headRefOid",
        lambda: tete(
            "d", 1, lanceur=lambda *a, **k: Rendu(0, '{"headRefOid": "abc123"}'), dors=dors
        ),
        "abc123",
    )
    verifie(
        "les ateliers en vol comptent ce qui n est pas « completed »",
        lambda: ateliers_en_vol(
            "d",
            "abc",
            lanceur=lambda *a, **k: Rendu(
                0,
                json.dumps(
                    {
                        "workflow_runs": [
                            {"status": "completed"},
                            {"status": "in_progress"},
                            {"status": "queued"},
                        ]
                    }
                ),
            ),
            dors=dors,
        ),
        2,
    )
    verifie(
        "et une charge sans « workflow_runs » est une panne, pas un zero",
        lambda: ateliers_en_vol("d", "abc", lanceur=lambda *a, **k: Rendu(0, "{}"), dors=dors),
        None,
    )

    # ⟨et le VRAI `subprocess`, sur un PATH vide⟩ Les coutures ci-dessus prouvent les branches ; elles
    # ne prouvent pas que `subprocess.run` leve bien ce que j attrape. Deux familles de cas, parce
    # qu un objet fabrique a la main ne prouve jamais la lecture de la vraie chose.
    chemin = os.environ.get("PATH", "")
    try:
        os.environ["PATH"] = str(pathlib.Path(__file__).resolve().parent / "aucun-outil-ici")
        verifie(
            "« gh » absent du PATH rend None, et ne leve pas",
            lambda: interroge("d", 1, dors=dors),
            None,
        )
        verifie(
            "et la lecture des ateliers rend None pareillement",
            lambda: ateliers_en_vol("d", "abc", dors=dors),
            None,
        )
    finally:
        os.environ["PATH"] = chemin

    # Le compte se DERIVE du harnais : un litteral reste juste le jour ou on l ecrit et
    # faux au cas suivant (#5744).
    print(
        f"\n{echecs.joues()} cas joue(s) : la surveillance d une demande, ses verdicts et la lecture des ateliers."
    )
    return echecs()


if __name__ == "__main__":
    if "--auto-test" in sys.argv[1:2]:
        sys.exit(_auto_test())
    if len(sys.argv) < 3:
        print(__doc__.strip().splitlines()[2].strip(), file=sys.stderr)
        raise SystemExit(2)
    sys.exit(
        surveille(
            sys.argv[1],
            int(sys.argv[2]),
            int(sys.argv[3]) if len(sys.argv) > 3 else BUDGET_PAR_DEFAUT,
        )
    )
