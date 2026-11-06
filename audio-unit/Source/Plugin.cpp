#include <juce_audio_utils/juce_audio_utils.h>
#include <BinaryData.h>
#include <array>
#include <atomic>
#include <memory>
#include <optional>
#include <vector>
#include <cmath>

namespace {
constexpr int tracks=8, steps=32, banks=8;
const char* names[tracks]={"KICK","SNARE","CLAP","C.HAT","O.HAT","RIM","TOM","PERC"};
const char* artistNames[]={"Martin Death","Gondi","Xande","Clay","Init"};
const char* presetNames[5][3]={{"Afterhours","Glitch Vein","Leftfield Garage"},{"Tunnel","Runs and Hits","Pressure Room"},{"Static Motion","Blue Circuit","Fragments"},{"Warm Dust","Raw Shuffle","Low Tide"},{"Phase One","Offset","Night Shift"}};
const char* defaults[tracks]={BinaryData::Kick_wav,BinaryData::Snare_wav,BinaryData::Clap_wav,
 BinaryData::ClosedHat_wav,BinaryData::OpenHat_wav,BinaryData::Rim_wav,BinaryData::Tom_wav,BinaryData::Perc_wav};
const int sizes[tracks]={BinaryData::Kick_wavSize,BinaryData::Snare_wavSize,BinaryData::Clap_wavSize,
 BinaryData::ClosedHat_wavSize,BinaryData::OpenHat_wavSize,BinaryData::Rim_wavSize,BinaryData::Tom_wavSize,BinaryData::Perc_wavSize};
struct Sample { std::vector<float> data; double rate=44100.; };
std::shared_ptr<Sample> decode(juce::InputStream* stream) {
 std::unique_ptr<juce::InputStream> owned(stream);
 if (!owned) return {};
 juce::WavAudioFormat fmt;
 std::unique_ptr<juce::AudioFormatReader> reader(fmt.createReaderFor(owned.release(),true));
 if (!reader || reader->lengthInSamples<1 || reader->lengthInSamples>44100*30) return {};
 auto sample=std::make_shared<Sample>(); sample->rate=reader->sampleRate;
 const auto len=static_cast<int>(reader->lengthInSamples);
 juce::AudioBuffer<float> buf(2,len); buf.clear();
 if (!reader->read(&buf,0,len,0,true,reader->numChannels>1)) return {};
 sample->data.resize(static_cast<size_t>(len));
 for(int i=0;i<len;++i) sample->data[static_cast<size_t>(i)]=(buf.getSample(0,i)+(reader->numChannels>1?buf.getSample(1,i):buf.getSample(0,i)))*.5f;
 return sample;
}
struct Hit { bool on=false; float velocity=.85f; float chance=1.f; int shift=0; };
struct Voice { std::shared_ptr<Sample> sample; double position=0,rate=1; float gain=1; bool active=false; int track=0; float lp=0; int age=0; };
}
class Ritual final: public juce::AudioProcessor {
public:
 Ritual():AudioProcessor(BusesProperties().withOutput("Output",juce::AudioChannelSet::stereo(),true)) {
  for(int t=0;t<tracks;++t) {
   samples[t]=decode(new juce::MemoryInputStream(defaults[t],static_cast<size_t>(sizes[t]),false));
   if (!samples[t]) samples[t]=std::make_shared<Sample>();
  }
  for(int b=0;b<banks;++b) {
   for(int s=0;s<steps;s+=4) patterns[b][0][s].on=true;
   for(int s=4;s<steps;s+=8) patterns[b][1][s].on=true;
   for(int s=2;s<steps;s+=4) patterns[b][3][s].on=true;
   if(b>=1)for(int s=6;s<steps;s+=8)patterns[b][4][s].on=true;
   if(b>=2)for(int s=3;s<steps;s+=8){patterns[b][7][s].on=true;patterns[b][7][s].velocity=.52f;}
   if(b>=3)for(int s=10;s<steps;s+=16)patterns[b][0][s].on=true;
   if(b>=4)for(int s=15;s<steps;s+=16){patterns[b][5][s].on=true;patterns[b][5][s].chance=.65f;}
   if(b>=5)for(int s=7;s<steps;s+=8){patterns[b][2][s].on=true;patterns[b][2][s].velocity=.46f;}
   if(b>=6)for(int s=14;s<steps;s+=16)patterns[b][6][s].on=true;
   if(b>=7)for(int s=0;s<steps;s+=16)patterns[b][0][s].on=false;
  }
  for(int t=0;t<tracks;++t){attacks[t].store(0);decays[t].store(0);filters[t].store(1);}
 }
 const juce::String getName() const override { return "DRUMS"; }
 void prepareToPlay(double sr,int) override { outputRate=sr; lastStep=-1; for(auto& v:voices)v.active=false; }
 void releaseResources() override {}
 bool isBusesLayoutSupported(const BusesLayout& l) const override { return l.getMainOutputChannelSet()==juce::AudioChannelSet::stereo(); }
 bool acceptsMidi() const override {return true;}
 bool producesMidi() const override {return false;}
 bool isMidiEffect() const override {return false;}
 double getTailLengthSeconds() const override {return 0;}
 int getNumPrograms() override {return 1;}
 int getCurrentProgram() override {return 0;}
 void setCurrentProgram(int) override {}
 const juce::String getProgramName(int) override {return {};}
 void changeProgramName(int,const juce::String&) override {}
 bool hasEditor() const override {return true;}
 juce::AudioProcessorEditor* createEditor() override;
 juce::CriticalSection stateLock;
 Hit patterns[banks][tracks][steps]{};
 std::array<std::shared_ptr<Sample>,tracks> samples;
 std::array<juce::String,tracks> samplePaths{};
 int bank=0,length=16,artist=0,preset=0; float swing=0.f, variation=0.f;
 std::array<float,tracks> levels{1,1,1,1,1,1,1,1},tunes{1,1,1,1,1,1,1,1};
 std::array<std::atomic<float>,tracks> attacks{},decays{},filters{};
 std::atomic<double> hostBpm{128.};
 void choosePreset(int a,int pr) {
  const juce::ScopedLock sl(stateLock);artist=juce::jlimit(0,4,a);preset=juce::jlimit(0,2,pr);bank=0;
  for(auto& pat:patterns)for(auto& tr:pat)for(auto& h:tr)h=Hit{};
  for(int b=0;b<banks;++b)for(int s=0;s<steps;++s){
   const int x=(s*19+b*7+artist*13+preset*11)%37;
   patterns[b][0][s].on=(s%4==0 || (artist==2&&x==1));
   patterns[b][1][s].on=s%8==4;
   patterns[b][3][s].on=(s%2==0 || x<3);
   patterns[b][4][s].on=s%8==6;
   if(x<(artist==3?8:4)){int t=(artist==1?5:artist==4?7:2);patterns[b][t][s].on=true;patterns[b][t][s].velocity=.55f;}
  }
  samplePaths.fill({});
  for(int t=0;t<tracks;++t) {samples[t]=decode(new juce::MemoryInputStream(defaults[t],static_cast<size_t>(sizes[t]),false));levels[t]=1;tunes[t]=1;attacks[t].store(0);decays[t].store(0);filters[t].store(1);}
 }
 std::atomic<int> displayedStep{-1};
 std::atomic<bool> previewRunning{false}, previewReset{false};
 bool loadSample(int t,const juce::File& f) {
  if(t<0||t>=tracks||!f.existsAsFile()) return false;
  auto decoded=decode(f.createInputStream().release());
  if(!decoded) return false;
  const juce::ScopedLock sl(stateLock); samples[t]=std::move(decoded); samplePaths[t]=f.getFullPathName(); return true;
 }
 void processBlock(juce::AudioBuffer<float>& buffer,juce::MidiBuffer& midi) override {
  buffer.clear(); const int n=buffer.getNumSamples();
  const int audition=auditionTrack.exchange(-1);
  if(audition>=0 && pendingCount<static_cast<int>(pending.size())) pending[pendingCount++]={audition,0,.9f};
  for(const auto m:midi) if(m.getMessage().isNoteOn()) {
   const int t=m.getMessage().getNoteNumber()-36;
   if(t>=0&&t<tracks && pendingCount<static_cast<int>(pending.size())) pending[pendingCount++]={t,m.samplePosition,m.getMessage().getFloatVelocity()};
  }
  auto* ph=getPlayHead(); auto pos=ph?ph->getPosition():std::optional<juce::AudioPlayHead::PositionInfo>{};
  if(pos && pos->getIsPlaying() && pos->getPpqPosition() && pos->getBpm() && *pos->getBpm()>0) {
   if(!wasHostPlaying){lastStep=-1;wasHostPlaying=true;}
   const double ppq=*pos->getPpqPosition(), bpm=*pos->getBpm();hostBpm.store(bpm);
   int activeLength=16;if(stateLock.tryEnter()){activeLength=length;stateLock.exit();}
   // Step boundaries are calculated in musical time, so playback follows Logic's transport.
   for(int s=0;s<n;++s) {
    const double beat=ppq+static_cast<double>(s)*bpm/(60.*outputRate);
    const int64_t absolute=static_cast<int64_t>(std::floor(beat*4.+1e-9));
    if(absolute!=lastStep) {
     lastStep=absolute;
     const int step=static_cast<int>((absolute%activeLength+activeLength)%activeLength);
     displayedStep.store(step);
     schedule(step,s,n,bpm);
    }
   }
  } else if(previewRunning.load()) {
   if(wasHostPlaying){lastStep=-1;previewSamples=0;wasHostPlaying=false;}
   if(previewReset.exchange(false)){previewSamples=0;lastStep=-1;}
   const double bpm=juce::jlimit(40.,300.,hostBpm.load());
   int activeLength=16;if(stateLock.tryEnter()){activeLength=length;stateLock.exit();}
   for(int s=0;s<n;++s){
    const auto absolute=static_cast<int64_t>(std::floor(previewSamples*4.*bpm/(60.*outputRate)));
    if(absolute!=lastStep){lastStep=absolute;const int step=static_cast<int>(absolute%activeLength);displayedStep.store(step);schedule(step,s,n,bpm);}
    ++previewSamples;
   }
  } else {lastStep=-1;wasHostPlaying=false;displayedStep.store(-1);previewSamples=0;}
  for(int s=0;s<n;++s) {
   for(int k=0;k<pendingCount;++k)if(pending[k].offset==s)trigger(pending[k].track,pending[k].velocity);
   for(auto& v:voices) if(v.active && v.sample) {
    if(v.position>=v.sample->data.size()) {v.active=false;continue;}
    const auto i=static_cast<size_t>(v.position);
    const auto j=std::min(i+1,v.sample->data.size()-1);
    const float x=static_cast<float>(v.position-static_cast<double>(i));
    float value=(v.sample->data[i]*(1-x)+v.sample->data[j]*x)*v.gain;
    const auto t=v.track;const float attack=attacks[t].load(),decay=decays[t].load(),filter=filters[t].load();
    const float attackSamples=std::max(1.f,attack*.08f*static_cast<float>(outputRate));
    const float env=std::min(1.f,static_cast<float>(v.age)/attackSamples)*std::exp(-decay*5.f*static_cast<float>(v.age)/static_cast<float>(outputRate));
    const float coef=juce::jlimit(.001f,1.f,filter*filter*.45f);v.lp+=coef*(value-v.lp);value=v.lp*env;++v.age;
    buffer.addSample(0,s,value);buffer.addSample(1,s,value);
    v.position+=v.rate;
   }
  }
  int remaining=0;
  for(int k=0;k<pendingCount;++k)if(pending[k].offset>=n) {pending[k].offset-=n;pending[remaining++]=pending[k];}
  pendingCount=remaining;
  for(int c=0;c<buffer.getNumChannels();++c) buffer.applyGain(c,0,n,.65f);
 }
 void getStateInformation(juce::MemoryBlock& out) override {
  const juce::ScopedLock sl(stateLock);
  juce::XmlElement root("Ritual"); root.setAttribute("version",1);
  root.setAttribute("artist",artist);root.setAttribute("preset",preset);root.setAttribute("bank",bank);root.setAttribute("length",length);root.setAttribute("swing",swing);root.setAttribute("variation",variation);
  for(int t=0;t<tracks;++t) {auto* track=root.createNewChildElement("Track");track->setAttribute("index",t);track->setAttribute("path",samplePaths[t]);track->setAttribute("level",levels[t]);track->setAttribute("tune",tunes[t]);track->setAttribute("attack",attacks[t].load());track->setAttribute("decay",decays[t].load());track->setAttribute("filter",filters[t].load());}
  for(int b=0;b<banks;++b) for(int t=0;t<tracks;++t) for(int s=0;s<steps;++s) {
   const auto& h=patterns[b][t][s]; if(!h.on && h.velocity==.85f && h.chance==1.f && h.shift==0) continue;
   auto* e=root.createNewChildElement("Step"); e->setAttribute("b",b);e->setAttribute("t",t);e->setAttribute("s",s);
   e->setAttribute("on",h.on);e->setAttribute("v",h.velocity);e->setAttribute("p",h.chance);e->setAttribute("shift",h.shift);
  }
  copyXmlToBinary(root,out);
 }
 void setStateInformation(const void* data,int size) override {
  auto xml=getXmlFromBinary(data,size); if(!xml||!xml->hasTagName("Ritual")) return;
  std::array<juce::String,tracks> paths;
  {
   const juce::ScopedLock sl(stateLock);
   artist=juce::jlimit(0,4,xml->getIntAttribute("artist",0));preset=juce::jlimit(0,2,xml->getIntAttribute("preset",0));
   bank=juce::jlimit(0,7,xml->getIntAttribute("bank",0));length=xml->getIntAttribute("length",16)==32?32:16;
   swing=juce::jlimit(0.f,.7f,static_cast<float>(xml->getDoubleAttribute("swing",0)));
   variation=juce::jlimit(0.f,1.f,static_cast<float>(xml->getDoubleAttribute("variation",0)));
   for(auto& pat:patterns)for(auto& tr:pat)for(auto& h:tr) h=Hit{};
   for(auto* e:xml->getChildIterator()) {
    if(e->hasTagName("Track")) {int t=e->getIntAttribute("index",-1);if(t>=0&&t<tracks){paths[t]=e->getStringAttribute("path");levels[t]=juce::jlimit(0.f,2.f,static_cast<float>(e->getDoubleAttribute("level",1)));tunes[t]=juce::jlimit(.5f,2.f,static_cast<float>(e->getDoubleAttribute("tune",1)));attacks[t].store(juce::jlimit(0.f,1.f,static_cast<float>(e->getDoubleAttribute("attack",0))));decays[t].store(juce::jlimit(0.f,1.f,static_cast<float>(e->getDoubleAttribute("decay",0))));filters[t].store(juce::jlimit(.05f,1.f,static_cast<float>(e->getDoubleAttribute("filter",1))));}}
    if(e->hasTagName("Step")) {int b=e->getIntAttribute("b",-1),t=e->getIntAttribute("t",-1),s=e->getIntAttribute("s",-1);
     if(b<0||b>=banks||t<0||t>=tracks||s<0||s>=steps)continue;
     auto& h=patterns[b][t][s];h.on=e->getBoolAttribute("on");h.velocity=juce::jlimit(0.f,1.f,static_cast<float>(e->getDoubleAttribute("v",.85)));
     h.chance=juce::jlimit(0.f,1.f,static_cast<float>(e->getDoubleAttribute("p",1)));
     h.shift=juce::jlimit(-35,35,e->getIntAttribute("shift",0));
    }
   }
  }
  for(int t=0;t<tracks;++t) if(paths[t].isNotEmpty()) loadSample(t,juce::File(paths[t]));
 }

 bool writeMidi(const juce::File& file,int lane) {
  juce::MidiMessageSequence sequence;
  Hit copy[tracks][steps];int count;
  {const juce::ScopedLock sl(stateLock);count=length;for(int t=0;t<tracks;++t)for(int n=0;n<count;++n)copy[t][n]=patterns[bank][t][n];}
  for(int n=0;n<count;++n)for(int t=0;t<tracks;++t)if((lane<0||lane==t)&&copy[t][n].on) {
   auto& h=copy[t][n];const double tick=std::max(0.,n*240.+h.shift*hostBpm.load()*960./60000.);
   auto on=juce::MidiMessage::noteOn(10,36+t,juce::jlimit(0.f,1.f,h.velocity));on.setTimeStamp(tick);
   auto off=juce::MidiMessage::noteOff(10,36+t);off.setTimeStamp(tick+100);
   sequence.addEvent(on);sequence.addEvent(off);
  }
  sequence.updateMatchedPairs();juce::MidiFile midi;midi.setTicksPerQuarterNote(960);midi.addTrack(sequence);
  auto out=file.createOutputStream();return out && midi.writeTo(*out);
 }
 bool writeStems(const juce::File& directory) {
  constexpr int sr=44100;const double bpm=juce::jlimit(40.,300.,hostBpm.load());
  int count;float exportSwing;Hit copy[tracks][steps];std::array<std::shared_ptr<Sample>,tracks> clip;
  std::array<float,tracks> gain,pitch,attack,decay,filter;
  {const juce::ScopedLock sl(stateLock);count=length;exportSwing=swing;
   for(int t=0;t<tracks;++t){clip[t]=samples[t];gain[t]=levels[t];pitch[t]=tunes[t];attack[t]=attacks[t].load();decay[t]=decays[t].load();filter[t]=filters[t].load();
    for(int n=0;n<count;++n)copy[t][n]=patterns[bank][t][n];}}
  const int frames=static_cast<int>((count*15./bpm+1.)*sr);
  juce::AudioBuffer<float> mix(2,frames);mix.clear();
  juce::WavAudioFormat wav;bool success=true;
  for(int t=0;t<tracks;++t){juce::AudioBuffer<float> audio(2,frames);audio.clear();
   if(clip[t] && !clip[t]->data.empty())for(int n=0;n<count;++n)if(copy[t][n].on){auto& h=copy[t][n];
    int start=std::max(0,static_cast<int>((n*15./bpm+(n%2?exportSwing*7.5/bpm:0)+h.shift/1000.)*sr));
    double at=0,rate=clip[t]->rate/sr*pitch[t];float lp=0;int age=0;
    for(int j=start;j<frames && at<clip[t]->data.size();++j,at+=rate,++age){
     size_t i=static_cast<size_t>(at);float x=static_cast<float>(at-i);
     size_t next=std::min(i+1,clip[t]->data.size()-1);
     float val=(clip[t]->data[i]*(1-x)+clip[t]->data[next]*x)*h.velocity*gain[t];
     float env=std::min(1.f,age/std::max(1.f,attack[t]*.08f*sr))*std::exp(-decay[t]*5.f*age/sr);
     lp+=juce::jlimit(.001f,1.f,filter[t]*filter[t]*.45f)*(val-lp);
     audio.addSample(0,j,lp*env*.65f);audio.addSample(1,j,lp*env*.65f);
    }
   }
   for(int c=0;c<2;++c)mix.addFrom(c,0,audio,c,0,frames);
   auto output=directory.getChildFile(juce::String(t+1).paddedLeft('0',2)+"_"+names[t]+".wav");
   auto stream=output.createOutputStream();if(!stream){success=false;continue;}
   std::unique_ptr<juce::AudioFormatWriter> writer(wav.createWriterFor(stream.get(),sr,2,16,{},0));
   if(!writer){success=false;continue;}stream.release();success=writer->writeFromAudioSampleBuffer(audio,0,frames)&&success;
  }
  auto stream=directory.getChildFile("00_FULL_MIX.wav").createOutputStream();
  if(!stream)return false;
  std::unique_ptr<juce::AudioFormatWriter> writer(wav.createWriterFor(stream.get(),sr,2,16,{},0));
  if(!writer)return false;stream.release();return writer->writeFromAudioSampleBuffer(mix,0,frames)&&success;
 }
private:
 double outputRate=44100.,previewSamples=0; int64_t lastStep=-1;bool wasHostPlaying=false;
 std::array<Voice,48> voices{};
 struct Pending {int track,offset;float velocity;};
 std::array<Pending,128> pending{};int pendingCount=0;
 uint32_t seed=0x457a22b1;
 float random01(){seed^=seed<<13;seed^=seed>>17;seed^=seed<<5;return static_cast<float>(seed&0xffffff)/16777216.f;}
 void trigger(int t,float velocity) {
  if(!stateLock.tryEnter())return;
  auto sample=samples[t];const float level=levels[t],tune=tunes[t];stateLock.exit();
  if(!sample||sample->data.empty())return;
  for(auto& v:voices)if(!v.active){v={sample,0.,sample->rate/outputRate*tune,velocity*level,true,t,0,0};return;}
  auto& v=voices[0];v={sample,0.,sample->rate/outputRate*tune,velocity*level,true,t,0,0};
 }
 void schedule(int step,int offset,int blockSize,double bpm) {
  Hit row[tracks];float currentSwing,currentVar;int currentBank;
  if(!stateLock.tryEnter())return;
  currentBank=bank;currentSwing=swing;currentVar=variation;
  for(int t=0;t<tracks;++t)row[t]=patterns[currentBank][t][step];
  stateLock.exit();
  const int stepSamples=static_cast<int>(outputRate*15./bpm);
  for(int t=0;t<tracks;++t) {
   auto h=row[t]; bool on=h.on;
   // Variation adds occasional ghost hats, shuffled snare echoes and percussion.
   if(!on && currentVar>0 && (t==3||t==5||t==7) && random01()<currentVar*.22f) {on=true;h.velocity=.28f;}
   if(!on||random01()>h.chance)continue;
   const int swingDelay=(step&1)?static_cast<int>(stepSamples*currentSwing*.5f):0;
   const int when=std::max(0,offset+swingDelay+static_cast<int>(h.shift*outputRate/1000.));
   if(pendingCount<static_cast<int>(pending.size())) pending[pendingCount++]={t,when,h.velocity};
  }
 }
public:
 // Used by the editor for immediate audition, independent of transport.
 void audition(int t){ if(t<0||t>=tracks)return;const juce::ScopedLock sl(stateLock); auditionTrack.store(t); }
 std::atomic<int> auditionTrack{-1};
};
class DrumsEditor final:public juce::AudioProcessorEditor,private juce::Timer {
public:
 explicit DrumsEditor(Ritual& p):juce::AudioProcessorEditor(p),proc(p) {setSize(1200,760);startTimerHz(15);}
 void paint(juce::Graphics& g) override {
  const auto a=juce::Colour(0xffe4f856), copper=juce::Colour(0xffed843b),blue=juce::Colour(0xff69aaff),sand=juce::Colour(0xffe0b477),violet=juce::Colour(0xffdf75e6);
  const juce::Colour accents[]={a,copper,blue,sand,violet};int artist,pre,bank,length;float swing,breakAmount;
  Hit row[tracks][16];std::shared_ptr<Sample> sample;juce::String path;
  {const juce::ScopedLock sl(proc.stateLock);artist=proc.artist;pre=proc.preset;bank=proc.bank;length=proc.length;swing=proc.swing;breakAmount=proc.variation;
   sample=proc.samples[selected];path=proc.samplePaths[selected];for(int t=0;t<tracks;++t)for(int i=0;i<16;++i)row[t][i]=proc.patterns[bank][t][i+page*16];}
  const auto accent=accents[juce::jlimit(0,4,artist)];
  g.fillAll(juce::Colour(0xff101111));
  g.setColour(juce::Colour(0xff272928));g.fillRoundedRectangle(8.f,8.f,1184.f,744.f,11.f);
  g.setColour(juce::Colour(0xff727671));g.drawRoundedRectangle(8.f,8.f,1184.f,744.f,11.f,2.f);
  // Hardware header, detented artist selector, preset selector.
  g.setColour(accent);g.fillRect(30,35,5,35);g.fillRect(39,35,5,35);g.fillRect(48,35,5,35);
  label(g,"MARBLE SMOKE",63,28,315,34,26,juce::Colours::whitesmoke,true);
  label(g,"DRUMS",65,62,175,19,13,accent,true);
  g.setColour(juce::Colour(0xff0a110c));g.fillRoundedRectangle(370.f,22.f,382.f,72.f,6.f);
  g.setColour(juce::Colour(0xff58634f));g.drawRoundedRectangle(370.f,22.f,382.f,72.f,6.f,1.f);
  label(g,juce::String(artistNames[artist]).toUpperCase()+" / "+juce::String(presetNames[artist][pre]).toUpperCase(),385,38,360,35,23,accent,true);
  knob(g,865,57,accent,-60+artist*30,true);knob(g,1040,57,accent,-45+pre*45,false);
  label(g,"ARTIST  ◀     ▶",797,96,137,20,12,juce::Colours::lightgrey,true);
  label(g,"PRESET  ◀     ▶",974,96,137,20,12,juce::Colours::lightgrey,true);
  // Performance pads.
  panel(g,25,126,565,322);panel(g,603,126,568,322);
  label(g,"01 / PERFORMANCE PADS",39,137,300,23,13,juce::Colours::whitesmoke,true);
  label(g,"C1 – G1 · CLICK TO AUDITION",323,138,254,22,10,juce::Colours::grey,false);
  for(int t=0;t<tracks;++t){int x=39+(t%4)*135,y=174+(t/4)*127;
   g.setColour(t==selected?accent:juce::Colour(0xff454946));g.fillRoundedRectangle(static_cast<float>(x),static_cast<float>(y),126.f,116.f,7.f);
   g.setColour(juce::Colour(0xff1b1e1d));g.fillRoundedRectangle(static_cast<float>(x+4),static_cast<float>(y+4),118.f,108.f,5.f);
   label(g,names[t],x+10,y+10,105,23,14,juce::Colours::whitesmoke,true);
   label(g,"MIDI "+juce::String(36+t),x+10,y+32,104,16,10,juce::Colours::grey,false);
   if(t==selected){g.setColour(accent);g.fillRoundedRectangle(static_cast<float>(x+9),static_cast<float>(y+96),45.f,4.f,2.f);}
   drawMiniWave(g,{x+12,y+57,99,25},accent,t);
  }
  label(g,"02 / SAMPLE EDIT",617,137,300,23,13,juce::Colours::whitesmoke,true);
  g.setColour(juce::Colour(0xff0a0d0c));g.fillRoundedRectangle(619.f,173.f,536.f,125.f,5.f);
  drawWave(g,{630,189,514,94},accent,sample);
  label(g,path.isNotEmpty()?juce::File(path).getFileName():"DEFAULT / "+juce::String(names[selected])+".WAV",628,302,352,20,12,juce::Colours::lightgrey,false);
  button(g,{1010,300,137,26},"LOAD WAV",accent,false);
  g.setColour(juce::Colour(0xff111413));g.fillRoundedRectangle(619.f,328.f,536.f,29.f,4.f);
  label(g,"ARTIST SIGNATURE",632,331,159,22,10,juce::Colours::grey,true);
  label(g,artistNames[artist],808,330,330,23,16,accent,false);
  for(int k=0;k<5;++k){int x=672+k*103;float value=parameter(k);
   knob(g,x,391,accent,-135+value*270,false);
   label(g,juce::StringArray{"TUNE","DECAY","ATTACK","FILTER","LEVEL"}[k],x-37,420,75,16,11,juce::Colours::lightgrey,true);
  }
  // Single row of sixteen large TR-style step keys, with voice and pattern strips.
  panel(g,25,466,1146,273);label(g,"03 / STEP SEQUENCER",40,477,281,23,13,juce::Colours::whitesmoke,true);
  label(g,"SYNC "+juce::String(proc.hostBpm.load(),1)+" BPM",322,478,178,21,12,accent,true);
  button(g,{516,475,87,27},length==16?"16 STEPS":"32 STEPS",accent,false);
  button(g,{609,475,90,27},page==0?"PAGE 1":"PAGE 2",accent,page==1);
  for(int t=0;t<tracks;++t)button(g,{40+t*108,515,102,30},names[t],accent,t==selected);
  label(g,"SWING "+juce::String(static_cast<int>(swing*100))+"%",933,514,100,30,12,accent,true);
  label(g,"BREAK "+juce::String(static_cast<int>(breakAmount*100))+"%",1038,514,110,30,12,accent,true);
  for(int i=0;i<16;++i){int x=42+i*70;auto h=row[selected][i];bool play=(proc.displayedStep.load()==i+page*16);
   label(g,juce::String(i+page*16+1).paddedLeft('0',2),x,549,65,20,12,juce::Colours::lightgrey,true);
   g.setColour(h.on?accent:juce::Colour(0xff737570));g.fillRoundedRectangle(static_cast<float>(x),571.f,64.f,75.f,5.f);
   g.setColour(h.on?accent.darker(.15f):juce::Colour(0xffb0a99c));g.fillRoundedRectangle(static_cast<float>(x+4),575.f,56.f,67.f,3.f);
   if(play){g.setColour(juce::Colours::white);g.drawRoundedRectangle(static_cast<float>(x-2),569.f,68.f,79.f,5.f,2.f);}
  }
  button(g,{40,677,95,43},"▶ PLAY",accent,false);button(g,{142,677,95,43},"■ STOP",accent,false);
  for(int b=0;b<banks;++b)button(g,{285+b*46,683,41,32},juce::String("ABCDEFGH").substring(b,b+1),accent,b==bank);
  button(g,{700,678,142,41},"MIDI ALL",accent,false);button(g,{850,678,142,41},"MIDI LANE",accent,false);button(g,{1000,678,142,41},"WAV STEMS",accent,false);
 }
 void mouseDown(const juce::MouseEvent& e) override {
  auto p=e.position.toInt();int x=p.x,y=p.y;
  if(y>=174&&y<417&&x>=39&&x<579){int col=(x-39)/135,row=(y-174)/127;if(col<4&&row<2){selected=row*4+col;proc.audition(selected);repaint();return;}}
  if(y>=571&&y<647&&x>=42&&x<1162){int index=(x-42)/70;if(index<16){const juce::ScopedLock sl(proc.stateLock);auto& h=proc.patterns[proc.bank][selected][index+page*16];h.on=!h.on;repaint();return;}}
  if(y>=515&&y<546&&x>=40&&x<904){int index=(x-40)/108;if(index<8){selected=index;proc.audition(index);repaint();return;}}
  if(y>=20&&y<110){if(x>=800&&x<965){int a;{const juce::ScopedLock sl(proc.stateLock);a=proc.artist;}proc.choosePreset((a+(x<865?4:1))%5,0);page=0;repaint();return;}
   if(x>=975&&x<1135){int a,b;{const juce::ScopedLock sl(proc.stateLock);a=proc.artist;b=proc.preset;}proc.choosePreset(a,(b+(x<1040?2:1))%3);page=0;repaint();return;}}
  if(y>=370&&y<419&&x>=631&&x<=1130){dragKnob=juce::jlimit(0,4,(x-631)/103);lastY=y;return;}
  if(y>=300&&y<326&&x>=1010&&x<1147){chooseWav();return;}
  if(y>=475&&y<505){if(x>=516&&x<603){const juce::ScopedLock sl(proc.stateLock);proc.length=proc.length==16?32:16;if(proc.length==16)page=0;repaint();return;}
   if(x>=609&&x<699){page=1-page;repaint();return;}}
  if(y>=677&&y<722){if(x>=40&&x<135){proc.previewReset.store(true);proc.previewRunning.store(true);repaint();return;}
   if(x>=142&&x<237){proc.previewRunning.store(false);repaint();return;}
   if(x>=285&&x<653){int b=(x-285)/46;if(b<8){const juce::ScopedLock sl(proc.stateLock);proc.bank=b;repaint();return;}}
   if(x>=700&&x<842){saveMidi(-1);return;}if(x>=850&&x<992){saveMidi(selected);return;}if(x>=1000&&x<1142){saveAudio();return;}}
  if(y>=510&&y<547&&x>=933){const juce::ScopedLock sl(proc.stateLock);if(x<1038)proc.swing=proc.swing>=.6f?0:proc.swing+.1f;else proc.variation=proc.variation>=.9f?0:proc.variation+.1f;repaint();}
 }
 void mouseDrag(const juce::MouseEvent& e) override {
  if(dragKnob<0)return;int dy=lastY-e.getPosition().y;lastY=e.getPosition().y;
  float v=juce::jlimit(0.f,1.f,parameter(dragKnob)+dy*.01f);
  const juce::ScopedLock sl(proc.stateLock);
  switch(dragKnob){case 0:proc.tunes[selected]=.5f+v*1.5f;break;case 1:proc.decays[selected].store(v);break;case 2:proc.attacks[selected].store(v);break;case 3:proc.filters[selected].store(.05f+v*.95f);break;case 4:proc.levels[selected]=v*2;break;}
  repaint();
 }
 void mouseUp(const juce::MouseEvent&) override {dragKnob=-1;}
private:
 Ritual& proc;int selected=0,page=0,dragKnob=-1,lastY=0;
 std::unique_ptr<juce::FileChooser> chooser;
 float parameter(int k) const {const juce::ScopedLock sl(proc.stateLock);switch(k){case 0:return (proc.tunes[selected]-.5f)/1.5f;case 1:return proc.decays[selected].load();case 2:return proc.attacks[selected].load();case 3:return (proc.filters[selected].load()-.05f)/.95f;default:return proc.levels[selected]/2;}}
 void timerCallback() override {repaint(25,466,1146,200);}
 void label(juce::Graphics& g,const juce::String& text,int x,int y,int w,int h,int size,juce::Colour color,bool bold) const {
  g.setColour(color);g.setFont(juce::FontOptions(static_cast<float>(size)).withStyle(bold?"Bold":"Regular"));g.drawFittedText(text,x,y,w,h,juce::Justification::centredLeft,1);
 }
 void panel(juce::Graphics& g,int x,int y,int w,int h){g.setColour(juce::Colour(0xff171a19));g.fillRoundedRectangle(static_cast<float>(x),static_cast<float>(y),static_cast<float>(w),static_cast<float>(h),7.f);g.setColour(juce::Colour(0xff535a54));g.drawRoundedRectangle(static_cast<float>(x),static_cast<float>(y),static_cast<float>(w),static_cast<float>(h),7.f,1.f);}
 void button(juce::Graphics& g,juce::Rectangle<int> r,const juce::String& text,juce::Colour a,bool active){g.setColour(active?a:juce::Colour(0xff3d423e));g.fillRoundedRectangle(r.toFloat(),4.f);g.setColour(active?juce::Colour(0xff151717):juce::Colours::whitesmoke);g.setFont(juce::FontOptions(12.f).withStyle("Bold"));g.drawFittedText(text,r,juce::Justification::centred,1);}
 void knob(juce::Graphics& g,int x,int y,juce::Colour accent,float degrees,bool detents){g.setColour(juce::Colour(0xff080909));g.fillEllipse(static_cast<float>(x-33),static_cast<float>(y-33),66.f,66.f);g.setColour(juce::Colour(0xff565b58));g.fillEllipse(static_cast<float>(x-29),static_cast<float>(y-29),58.f,58.f);g.setColour(juce::Colour(0xff222625));g.fillEllipse(static_cast<float>(x-25),static_cast<float>(y-25),50.f,50.f);g.setColour(accent);auto rad=juce::degreesToRadians(degrees-90);g.drawLine(static_cast<float>(x)+12*std::cos(rad),static_cast<float>(y)+12*std::sin(rad),static_cast<float>(x)+24*std::cos(rad),static_cast<float>(y)+24*std::sin(rad),3.f);if(detents)for(int i=0;i<5;++i){auto ang=juce::degreesToRadians(-150.f+i*30.f);g.fillEllipse(static_cast<float>(x+36*std::sin(ang)-2),static_cast<float>(y-36*std::cos(ang)-2),4.f,4.f);}}
 void drawMiniWave(juce::Graphics& g,juce::Rectangle<int> r,juce::Colour a,int t){g.setColour(a.withAlpha(.7f));juce::Path p;for(int x=0;x<r.getWidth();++x){float env=std::exp(-x*.035f);float amp=std::sin(x*(t+3)*.6f)*env*r.getHeight()*.4f;float y=r.getCentreY()+amp;if(x==0)p.startNewSubPath(static_cast<float>(r.getX()+x),y);else p.lineTo(static_cast<float>(r.getX()+x),y);}g.strokePath(p,juce::PathStrokeType(1.f));}
 void drawWave(juce::Graphics& g,juce::Rectangle<int> r,juce::Colour a,const std::shared_ptr<Sample>& s){if(!s||s->data.empty())return;g.setColour(a);juce::Path p;for(int x=0;x<r.getWidth();++x){size_t begin=static_cast<size_t>(static_cast<double>(x)*s->data.size()/r.getWidth());size_t end=std::min(s->data.size(),static_cast<size_t>(static_cast<double>(x+1)*s->data.size()/r.getWidth()));float peak=0;for(size_t i=begin;i<end;++i)peak=std::max(peak,std::abs(s->data[i]));float xx=static_cast<float>(r.getX()+x),v=peak*r.getHeight()*.47f;g.drawLine(xx,r.getCentreY()-v,xx,r.getCentreY()+v,1.f);}}
 void chooseWav(){chooser=std::make_unique<juce::FileChooser>("Load sample",juce::File{},"*.wav");chooser->launchAsync(juce::FileBrowserComponent::openMode|juce::FileBrowserComponent::canSelectFiles,[this](const juce::FileChooser& c){auto f=c.getResult();if(f.existsAsFile()&&!proc.loadSample(selected,f))juce::AlertWindow::showMessageBoxAsync(juce::MessageBoxIconType::WarningIcon,"DRUMS","Could not read this WAV");repaint();});}
 void saveMidi(int lane){chooser=std::make_unique<juce::FileChooser>("Export MIDI",juce::File::getSpecialLocation(juce::File::userDocumentsDirectory).getChildFile(lane<0?"DRUMS_ALL.mid":juce::String("DRUMS_")+names[lane]+".mid"),"*.mid");chooser->launchAsync(juce::FileBrowserComponent::saveMode|juce::FileBrowserComponent::canSelectFiles|juce::FileBrowserComponent::warnAboutOverwriting,[this,lane](const juce::FileChooser& c){auto f=c.getResult();if(f.getFullPathName().isNotEmpty()&&!proc.writeMidi(f,lane))juce::AlertWindow::showMessageBoxAsync(juce::MessageBoxIconType::WarningIcon,"DRUMS","MIDI export failed");});}
 void saveAudio(){chooser=std::make_unique<juce::FileChooser>("Export WAV stems into a folder");chooser->launchAsync(juce::FileBrowserComponent::openMode|juce::FileBrowserComponent::canSelectDirectories,[this](const juce::FileChooser& c){auto dir=c.getResult();if(dir.isDirectory()&&!proc.writeStems(dir))juce::AlertWindow::showMessageBoxAsync(juce::MessageBoxIconType::WarningIcon,"DRUMS","WAV export failed");});}
};
juce::AudioProcessorEditor* Ritual::createEditor(){return new DrumsEditor(*this);}
juce::AudioProcessor* JUCE_CALLTYPE createPluginFilter(){return new Ritual();}
