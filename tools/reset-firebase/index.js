// Ferramenta de reset TOTAL do backend Firebase do VaiVan.
// Apaga permanentemente: todos os usuarios do Authentication,
// todas as colecoes do Firestore e todos os arquivos do Storage.
// Uso manual e deliberado - NAO ha undo. Pede confirmacao digitada antes de agir.

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const admin = require('firebase-admin');

const EXE_DIR = path.dirname(process.execPath);
const SCRIPT_DIR = __dirname;

function findServiceAccountPath() {
  const candidates = [
    process.env.GOOGLE_APPLICATION_CREDENTIALS,
    path.join(process.cwd(), 'serviceAccountKey.json'),
    path.join(EXE_DIR, 'serviceAccountKey.json'),
    path.join(SCRIPT_DIR, 'serviceAccountKey.json'),
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  return null;
}

function ask(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => rl.question(question, (answer) => { rl.close(); resolve(answer); }));
}

async function deleteAllAuthUsers(auth) {
  console.log('\n[AUTH] Apagando usuarios...');
  let total = 0;
  let pageToken;
  do {
    const result = await auth.listUsers(1000, pageToken);
    if (result.users.length > 0) {
      const uids = result.users.map((u) => u.uid);
      const deleteResult = await auth.deleteUsers(uids);
      total += deleteResult.successCount;
      if (deleteResult.failureCount > 0) {
        console.warn(`  Falhas ao apagar ${deleteResult.failureCount} usuario(s):`);
        deleteResult.errors.forEach((e) => console.warn(`   - ${e.index}: ${e.error.message}`));
      }
      console.log(`  ... ${total} usuario(s) apagados`);
    }
    pageToken = result.pageToken;
  } while (pageToken);
  console.log(`[AUTH] Concluido. Total apagado: ${total}`);
}

async function deleteAllFirestoreData(db) {
  console.log('\n[FIRESTORE] Apagando colecoes...');
  const collections = await db.listCollections();
  if (collections.length === 0) {
    console.log('[FIRESTORE] Nenhuma colecao encontrada.');
    return;
  }
  for (const col of collections) {
    console.log(`  Apagando colecao "${col.id}"...`);
    await db.recursiveDelete(col);
    console.log(`  ... "${col.id}" apagada`);
  }
  console.log('[FIRESTORE] Concluido.');
}

async function deleteAllStorageFiles(bucket) {
  console.log('\n[STORAGE] Apagando arquivos...');
  try {
    const [files] = await bucket.getFiles();
    if (files.length === 0) {
      console.log('[STORAGE] Nenhum arquivo encontrado.');
      return;
    }
    await bucket.deleteFiles({ force: true });
    console.log(`[STORAGE] Concluido. ${files.length} arquivo(s) apagados.`);
  } catch (err) {
    console.error(`[STORAGE] Erro: ${err.message}`);
    throw err;
  }
}

async function main() {
  console.log('=== VaiVan - Reset total do Firebase ===');

  const keyPath = findServiceAccountPath();
  if (!keyPath) {
    console.error('\nERRO: nao encontrei "serviceAccountKey.json".');
    console.error('Coloque o arquivo de credencial (Firebase Console > Configuracoes do projeto >');
    console.error('Contas de servico > Gerar nova chave privada) na mesma pasta deste executavel,');
    console.error('ou defina a variavel de ambiente GOOGLE_APPLICATION_CREDENTIALS.');
    process.exit(1);
  }

  const serviceAccount = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  const projectId = serviceAccount.project_id;
  const storageBucket = process.env.STORAGE_BUCKET || `${projectId}.appspot.com`;

  console.log(`\nCredencial: ${keyPath}`);
  console.log(`Projeto Firebase: ${projectId}`);
  console.log(`Bucket de Storage: ${storageBucket}`);
  console.log('\nISSO VAI APAGAR PERMANENTEMENTE, SEM POSSIBILIDADE DE RECUPERACAO:');
  console.log('  - TODOS os usuarios do Firebase Authentication');
  console.log('  - TODAS as colecoes e documentos do Firestore');
  console.log('  - TODOS os arquivos do Storage');

  const confirmation = await ask('\nDigite exatamente APAGAR TUDO para confirmar: ');
  if (confirmation.trim() !== 'APAGAR TUDO') {
    console.log('\nConfirmacao incorreta. Operacao cancelada, nada foi apagado.');
    process.exit(0);
  }

  const doubleCheck = await ask(`Tem certeza? Isso vai destruir os dados do projeto "${projectId}" agora (sim/nao): `);
  if (doubleCheck.trim().toLowerCase() !== 'sim') {
    console.log('\nOperacao cancelada, nada foi apagado.');
    process.exit(0);
  }

  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    projectId,
    storageBucket,
  });

  const startedAt = Date.now();
  try {
    await deleteAllAuthUsers(admin.auth());
    await deleteAllFirestoreData(admin.firestore());
    await deleteAllStorageFiles(admin.storage().bucket());
  } catch (err) {
    console.error('\nFalha durante o reset:', err);
    process.exit(1);
  }

  const seconds = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`\n=== Reset concluido em ${seconds}s ===`);
}

main();
