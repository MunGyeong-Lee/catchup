import catchupBottle from './assets/catchup-bottle.png'

export default function CareerLibraryHeader() {
  return (
    <header className="career-library-header">
      <img src={catchupBottle} alt="" width="32" height="49" />
      <strong>Catchup</strong>
    </header>
  )
}
