const path = require('path');

module.exports = {
  mode: 'development',
  entry: './build/classes/kotlin/js/main/default/module.js',
  output: {
    filename: 'index.js',
    path: path.resolve(__dirname, 'build/distributions'),
    library: 'kmpGraphineApp',
    libraryTarget: 'umd',
  },
  devtool: 'source-map',
  devServer: {
    static: {
      directory: path.join(__dirname, 'build/distributions'),
    },
    port: 8080,
    open: true,
  },
};
