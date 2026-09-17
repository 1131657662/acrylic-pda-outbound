import SwaggerParser from '@apidevtools/swagger-parser';
import { fileURLToPath } from 'node:url';

const path = fileURLToPath(new URL('../../contracts/openapi.yaml', import.meta.url));
const api = await SwaggerParser.validate(path);
console.log(`OpenAPI ${api.openapi}: ${Object.keys(api.paths).length} paths validated.`);
