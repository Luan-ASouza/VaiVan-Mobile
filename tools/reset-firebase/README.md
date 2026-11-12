# Reset total do Firebase (VaiVan)

Ferramenta de uso manual que apaga **permanentemente** todos os usuários do
Firebase Authentication e zera **todo** o Firestore e o Storage do projeto
`vaivanvaicontigo`. Não existe undo — use com cuidado.

## 1. Gerar a credencial de administrador

1. Firebase Console → ⚙️ Configurações do projeto → **Contas de serviço**.
2. "Gerar nova chave privada" → baixa um `.json`.
3. Renomeie para `serviceAccountKey.json` e coloque nesta pasta (ou na mesma
   pasta do `.exe`, depois de gerado). **Nunca** commite esse arquivo —
   o `.gitignore` já o ignora.

## 2. Rodar direto com Node (sem gerar .exe)

```bash
cd tools/reset-firebase
npm install
npm start
```

## 3. Gerar o `.exe` (pra reusar sem precisar de Node instalado)

```bash
cd tools/reset-firebase
npm install
npm run build
```

Isso gera `dist/reset-firebase.exe`. Copie o `.exe` e o
`serviceAccountKey.json` juntos para onde quiser rodar — o executável
procura a credencial na própria pasta dele.

## 4. Executar

Dando duplo-clique no `.exe` (ou `node index.js`), a ferramenta:

1. Mostra o projeto Firebase e o bucket que vai afetar.
2. Pede para digitar `APAGAR TUDO` e depois confirmar com `sim`.
3. Só então apaga: usuários do Auth → coleções do Firestore → arquivos do
   Storage, com log de progresso.

Qualquer resposta diferente nas confirmações cancela a operação sem apagar
nada.
