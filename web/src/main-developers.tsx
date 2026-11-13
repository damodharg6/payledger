// Browser entry for /developers: hydrates the pre-rendered HTML (or renders it in dev).
// Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
import { StrictMode } from 'react';
import { createRoot, hydrateRoot } from 'react-dom/client';
import { DevelopersApp } from './apps';
import './styles/global.css';

const root = document.getElementById('root')!;
const app = <StrictMode><DevelopersApp /></StrictMode>;
if (root.firstElementChild) hydrateRoot(root, app);
else createRoot(root).render(app);
