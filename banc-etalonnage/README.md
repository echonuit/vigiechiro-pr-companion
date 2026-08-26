# Banc d'étalonnage des sondes d'écriture

Ce dossier n'est **pas** une dépendance du produit. Rien dans le dépôt ne l'importe, ne l'invoque ni
ne le suppose : s'il disparaît du poste, aucune vérification ne change de couleur. C'est la condition
posée par l'[ADR 4444], et elle a été mesurée le 2026-08-26 en retirant le dossier et en relançant
les gardes - 38 tests, verts des deux côtés.

## À quoi il sert, et à quoi il ne sert pas

Il **ne tourne aucun clip**. Un clip filmé contre lui serait le « convaincant et creux » de
l'[ADR 4142], en pire : on y verrait du vrai code serveur et on en conclurait qu'on a vu un dépôt.

Il sert à **écrire et déboguer hors ligne** les sondes d'écriture du contrat live, qui sont ensuite
tirées **une fois** contre la participation de rebut réelle. L'écart entre les deux tirs est la
mesure de dérive ; le banc seul n'établit rien.

## Le lancer

```bash
docker compose -f banc-etalonnage/compose.yml up -d --build

# Frapper un jeton sans OAuth, puis amorcer la base (l'ordre compte : l'amorçage rattache le site
# au compte que le premier appel fabrique).
curl -s -i http://localhost:8080/login/google | grep -oE 'token=[A-Z0-9]+' | head -1 | cut -d= -f2
docker compose -f banc-etalonnage/compose.yml exec -T mongo \
    mongosh --quiet vigiechiro < banc-etalonnage/amorcer.js
```

L'amorçage imprime l'identifiant de la participation qu'il pose. Une sonde se répète alors ainsi :

```bash
./mvnw -B -Papi-live test -Dtest=ContratApiVigieChiroLiveTest#<la sonde> \
  -Dvigiechiro.baseUrl=http://localhost:8080 \
  -Dvigiechiro.token=<le jeton frappé> \
  -Dvigiechiro.write=true \
  -Dvigiechiro.participationEssai=<la participation amorcée>
```

Le même appel sans `-Dvigiechiro.baseUrl` tire contre la **vraie plateforme**. C'est la seule
différence entre une répétition et un tir, et c'est voulu : la sonde ne change pas entre les deux.

## Le ranger

```bash
docker compose -f banc-etalonnage/compose.yml down -v
```

Mongo tourne sur un `tmpfs` sans volume nommé : rien ne survit. C'est délibéré - une base qui
survivrait ferait dériver l'état de départ d'un essai à l'autre sans que rien ne le dise.

## Trois choses qui coûtent une demi-heure si on ne les sait pas

**`runserver.py` écoute sur `127.0.0.1`.** Il fait `app.run(debug=True, port=…)` sans hôte, donc la
porte publiée ne mène à rien et la connexion est réinitialisée : le back paraît mort alors qu'il
tourne. Le compose surcharge la commande.

**La révision du back est épinglée** dans le `Dockerfile`. Toute la décision de l'ADR 4444 porte sur
la dérive d'un back figé : un banc qui suivrait la branche amont ne saurait plus dire contre quoi il
a étalonné une sonde. La bouger est une décision, pas une mise à jour.

**L'amorçage exige un site VERROUILLÉ.** `_validate_site` refuse sinon, par
« cannot create protocole on an unlocked site », et le message ne dit pas que le verrou est en cause.

## Ce qu'il a déjà servi à établir

Les deux comportements que #4356 exigeait avant d'enrichir le bouchon, tirés le 2026-08-26 :

| Comportement | Verdict | Le banc l'avait-il prédit ? |
|---|---|---|
| `If-Match` requis sur le `PATCH` d'une participation | **démenti** : `200` sans en-tête et avec un faux | oui |
| `numero` refusé à l'écriture | **confirmé** : `422 invalid field` | oui |

L'accord entre le banc et la production sur les deux est lui-même un résultat : sur ces contrats-là,
notre copie ne dérive pas.

[ADR 4142]: ../dev-docs/decisions/4142-un-cas-dit-ou-se-lit-son-verdict.md
[ADR 4444]: ../dev-docs/decisions/4444-un-back-local-etalonne-les-sondes-il-ne-tourne-aucun-clip.md
