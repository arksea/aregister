import { AregisterWebPage } from './app.po';

describe('aregister-web App', () => {
  let page: AregisterWebPage;

  beforeEach(() => {
    page = new AregisterWebPage();
  });

  it('should display welcome message', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('Welcome to app!');
  });
});
