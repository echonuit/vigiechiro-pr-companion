"""Ce qu est un EPIC, en UN seul endroit, parce que la divergence etait le defaut.

Quatre dispositifs lisaient cette notion, et trois en lisaient la fausse. Mesure du 2026-09-30, en
IMPORTANT le predicat de chacun plutot qu en reecrivant son motif :

    dispositif                                          sa definition      sa surface
    scripts/adr/loupe-4992-lots-sans-critere.py         l UNION, la juste  predicat local
    scripts/adr/loupe-4712-lots-multi-pr.py             le label seul      predicat local
    .github/scripts/verifie_cloture_consignee.py        le label seul      la REQUETE a la forge
    .github/scripts/verifie_specification_consignee.py  le label seul      la REQUETE a la forge

## Ce que la mauvaise definition coutait, mesure

Sur les issues OUVERTES : 40 EPIC par l union, 30 par le label. Dix etaient donc invisibles a trois
dispositifs sur quatre, dont le sas des suites #4562 et ses 43 sous-issues.

Sur les issues CLOSES, et c est la que ca cesse d etre une loupe qui montre trop peu pour devenir un
garde qui CONCLUT : 1 737 closes, 96 EPIC par le label, 154 par l union. Donc
`verifie_cloture_consignee.py` rendait un verdict sur « les EPIC clos sans trace de cloture » sans
avoir jamais regarde 58 d entre eux, et 23 de ces 58 n avaient effectivement aucune trace.

## Pourquoi ce module plutot que `forge.py`, sa voisine

`scripts/_commun/forge.py` porte l appel a `gh`, et son en-tete pose la regle qui decide ici : cette
couche « ne sait rien du domaine, elle sait lancer gh, lire un code, et distinguer trois silences ».
Savoir ce qu est un EPIC est du domaine. Le mettre la aurait melange les couches que ce module-ci a
justement ete ecrit pour separer.

## Pourquoi une UNION, et dans les deux sens

Ni l une ni l autre des deux populations ne contient l autre. Au 2026-09-30, sur les ouvertes,
38 portent le prefixe de titre et 30 le label, dont 28 les deux : **2 portent le label sans le
prefixe**, et 10 le prefixe sans le label. Une definition fondee sur le seul titre serait donc fausse
elle aussi, et c est le sens qu on oublie en corrigeant.

Ce chiffre a ete mesure faux une premiere fois : la soustraction `label - (label | titre)` est vide
PAR CONSTRUCTION, et son zero se lisait « aucun EPIC ne porte le label sans le titre ». La valeur ne
se lit pas, elle se derive de l inclusion-exclusion.

## Le troisieme prefixe

`[chantier]` compte autant que `[epic]`, et c est celui qu on rate : il ne contient pas le mot. Deux
EPIC ouverts le portaient au 2026-09-30, #4816 et #4817, et #4816 portait sept sous-issues que rien
ne lisait.

## Ou vivent les cas de ce module

Dans `verifie_grammaire()` plus bas, joues par l auto-test de `scripts/adr/loupe-4712-lots-multi-pr.py`
et par celui de `.github/scripts/verifie_cloture_consignee.py`. DEUX joueurs et non quatre, un de
chaque cote de la barriere : ce qui doit etre prouve est que la meme definition traverse les deux
paquets, et un seul joueur ne le montrerait pas.
"""

from __future__ import annotations

# Le label de la forge, et les prefixes de titre. Les trois comptent, et aucun ne se deduit des
# autres : un EPIC peut porter le label sans le prefixe, et l inverse.
LABEL = "epic"
PREFIXES = ("[epic]", "[chantier]")


def est_epic(issue: dict) -> bool:
    """L UNION du label et du titre : rater un chantier, c est ne pas poser la question.

    La formule vient de `loupe-4992-lots-sans-critere.py`, seul des quatre dispositifs a l avoir juste,
    et elle est reprise a l identique plutot que reecrite. Ce module existe pour qu il n y ait plus de
    seconde ecriture a garder d accord.
    """
    par_label = any(e.get("name") == LABEL for e in issue.get("labels") or [])
    titre = (issue.get("title") or "").lower().lstrip()
    return par_label or titre.startswith(PREFIXES)


def retenus_parmi(brut: list[dict], plafond: int) -> tuple[list[dict], bool]:
    """Les EPIC d une collecte brute, et si elle a TOUCHE son plafond.

    La decision vit ici, pure, et non dans l appel a la forge. Deux raisons.

    D abord elle se joue hors ligne, sans leurre ni `gh` fabrique : les cas de `verifie_grammaire`
    ci-dessous la traversent tous les trois, sous le plafond, AU plafond et au-dela.

    Ensuite `scripts/adr/verifie_gages_joues.py` ne resout pas un module hors de `scripts/` : son
    `_module_de` rend un chemin absolu la ou les imports ecrivent `_forge`, donc un gage declare dans
    `.github/scripts/` est toujours compte INERTE, meme joue. Mon gage y etait le premier de son
    espece, et la porte l a refuse. Le trou est dans ce garde, livre par #5594 ; le remede propre est
    de corriger `_module_de`, et ce n est pas ce lot.

    Le plafond se compare en `>=` et non `==` : un depassement d une seule issue est plus probable
    qu une egalite exacte, et le laisser passer rendrait un corpus tronque.
    """
    return [i for i in brut if est_epic(i)], len(brut) >= plafond


def verifie_grammaire() -> list[tuple[str, bool]]:
    """Les cas de la definition, dont les DEUX sens de l union et le prefixe qu on oublie.

    Le dernier cas est le seul qui rougirait si quelqu un remplacait l union par une intersection, ce
    qui est la faute symetrique de celle que ce module corrige : elle passerait les trois premiers.
    """
    label = {"title": "fix(x) : y", "labels": [{"name": LABEL}]}
    prefixe = {"title": "[epic] X", "labels": []}
    chantier = {"title": "[chantier] X", "labels": []}
    ni = {"title": "fix(x) : y", "labels": []}
    les_deux = {"title": "[epic] X", "labels": [{"name": LABEL}]}

    return [
        ("le LABEL seul suffit", est_epic(label)),
        ("le PREFIXE seul suffit, c est le sens qu on corrige", est_epic(prefixe)),
        ("« [chantier] » compte autant, et il ne contient pas le mot", est_epic(chantier)),
        ("ni l un ni l autre : ce n est pas un EPIC", not est_epic(ni)),
        # Sans ce cas, une INTERSECTION passerait les quatre precedents : `les_deux` la satisfait,
        # et `label` comme `prefixe` seraient rejetes sans que rien ne le dise.
        ("porter les DEUX ne change rien", est_epic(les_deux)),
        # Le titre se lit insensible a la casse ET aux blancs de tete : les deux ont ete vus dans le
        # depot, et une definition qui ne tolere ni l un ni l autre rate en silence.
        ("le titre se lit sans la casse", est_epic({"title": "[EPIC] X", "labels": []})),
        ("et sans les blancs de tete", est_epic({"title": "  [epic] X", "labels": []})),
        # Un prefixe qui n est pas en TETE ne compte pas : « suite de [epic] X » n est pas un EPIC.
        ("un prefixe au MILIEU ne compte pas", not est_epic({"title": "suite de [epic] X"})),
        # Les deux formes degradees que la forge rend vraiment : pas de champ, ou un champ nul.
        ("une issue sans champs ne plante pas", not est_epic({})),
        ("un titre nul ne plante pas", not est_epic({"title": None, "labels": None})),
        # La COLLECTE : ce qu elle garde, ce qu elle laisse, et le plafond. Les deux cliquets de
        # cloture demandaient `--label epic`, donc la forge filtrait pour eux et rien ne pouvait
        # les elargir localement.
        ("la collecte garde l EPIC par le LABEL", 1 in _numeros_retenus()),
        ("celui par le TITRE, que le filtre de la forge cachait", 2 in _numeros_retenus()),
        ("celui en « [chantier] » aussi", 3 in _numeros_retenus()),
        ("et laisse dehors l issue ordinaire", 4 not in _numeros_retenus()),
        ("sous le plafond, la collecte n est PAS tronquee", not _tronquee(9, 10)),
        # AU plafond et AU-DELA : la comparaison est `>=`. Un `==` laisserait passer le depassement
        # d une seule issue, qui est le cas le plus probable des deux.
        ("AU plafond, elle se dit TRONQUEE", _tronquee(10, 10)),
        ("et au-dela egalement", _tronquee(11, 10)),
    ]


# Le corpus des cas de collecte : un EPIC par le label, un par le titre, un en « [chantier] », et
# une issue ordinaire qui doit rester dehors.
_MELANGE = (
    {"number": 1, "title": "fix(x) : y", "labels": [{"name": LABEL}]},
    {"number": 2, "title": "[epic] par le titre", "labels": []},
    {"number": 3, "title": "[chantier] par le titre aussi", "labels": []},
    {"number": 4, "title": "fix(z) : une issue ordinaire", "labels": []},
)


def _numeros_retenus() -> list[int]:
    retenus, _ = retenus_parmi(list(_MELANGE), plafond=100)
    return [i["number"] for i in retenus]


def _tronquee(combien: int, plafond: int) -> bool:
    faux = [{"number": n, "title": "[epic] x", "labels": []} for n in range(combien)]
    _, tronquee = retenus_parmi(faux, plafond)
    return tronquee
